package no.nav.syfo.cronjob.dialogmotekandidat

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatOutdatedCronjob
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.util.toOffsetDatetime
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DialogmotekandidatOutdatedCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmotekandidatRepository = externalMockEnvironment.dialogmotekandidatRepository
    private val kafkaProducer = mockk<KafkaProducer<String, DialogmotekandidatEndringRecord>>()
    private val endringProducer = DialogmotekandidatEndringProducer(producer = kafkaProducer)
    private val personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val cutoff = LocalDate.now()
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = mockk(),
        dialogmotekandidatEndringProducer = endringProducer,
        database = database,
        dialogmotekandidatRepository = dialogmotekandidatRepository,
    )
    private val cronjob = DialogmotekandidatOutdatedCronjob(cutoff, dialogmotekandidatService)

    private fun getEndringer(p: Personident): List<DialogmotekandidatEndring> =
        dialogmotekandidatRepository.getDialogmotekandidatEndringer(p)

    @BeforeEach
    fun setup() {
        database.dropData()
        clearMocks(kafkaProducer)
        every { kafkaProducer.send(any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `creates LUKKET endring for kandidat before cutoff with no other endring`() {
        val kandidatBefore = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(kandidatBefore)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        val slotRecord = slot<ProducerRecord<String, DialogmotekandidatEndringRecord>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        assertFalse(slotRecord.captured.value().kandidat)
        val endringer = getEndringer(personident)
        assertEquals(2, endringer.size)
        val latest = endringer.first()
        assertEquals(DialogmotekandidatEndringArsak.LUKKET, latest.arsak)
        assertFalse(latest.kandidat)
    }

    @Test
    fun `updates kandidat before cutoff only once`() {
        val kandidatBefore = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        val otherKandidatBefore = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT)
            .copy(createdAt = cutoff.minusDays(100).toOffsetDatetime())
        val notKandidatBefore = generateDialogmotekandidatEndringFerdigstilt(UserConstants.ARBEIDSTAKER_2_PERSONIDENTNUMBER)
            .copy(createdAt = cutoff.minusDays(50).toOffsetDatetime())
        val kandidatAfter = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER)
            .copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        listOf(
            kandidatBefore,
            otherKandidatBefore,
            notKandidatBefore,
            kandidatAfter
        ).forEach { database.createDialogmotekandidatEndring(it) }
        var result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
    }

    @Test
    fun `creates no endring for person with no endring`() {
        cronjob.runJob()
        verify(exactly = 0) { kafkaProducer.send(any()) }
        assertTrue(getEndringer(personident).isEmpty())
    }

    @Test
    fun `creates no endring for not kandidat before cutoff`() {
        val notKandidatBefore = generateDialogmotekandidatEndringFerdigstilt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(notKandidatBefore)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(1, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }

    @Test
    fun `creates no endring for not kandidat after cutoff`() {
        val notKandidatAfter =
            generateDialogmotekandidatEndringFerdigstilt(personident).copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(notKandidatAfter)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(1, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }

    @Test
    fun `creates no endring for kandidat after cutoff`() {
        val kandidatAfter = generateDialogmotekandidatEndringStoppunkt(personident).copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(kandidatAfter)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(1, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }

    @Test
    fun `creates no endring for kandidat before and not kandidat after cutoff`() {
        val kandidatBefore = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        val notKandidatAfter = generateDialogmotekandidatEndringFerdigstilt(personident)
            .copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(kandidatBefore)
        database.createDialogmotekandidatEndring(notKandidatAfter)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(2, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }

    @Test
    fun `creates no endring for not kandidat before and kandidat after cutoff`() {
        val notKandidatBefore = generateDialogmotekandidatEndringFerdigstilt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        val kandidatAfter = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(notKandidatBefore)
        database.createDialogmotekandidatEndring(kandidatAfter)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(2, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }

    @Test
    fun `creates no endring for kandidat before and kandidat after cutoff`() {
        val kandidatBefore = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.minusDays(1).toOffsetDatetime())
        val kandidatAfter = generateDialogmotekandidatEndringStoppunkt(personident)
            .copy(createdAt = cutoff.plusDays(1).toOffsetDatetime())
        database.createDialogmotekandidatEndring(kandidatBefore)
        database.createDialogmotekandidatEndring(kandidatAfter)
        val result = cronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = getEndringer(personident)
        assertEquals(2, endringer.size)
        assertTrue(endringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET })
    }
}
