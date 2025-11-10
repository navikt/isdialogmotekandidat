package no.nav.syfo.cronjob.dialogmotekandidat

import io.mockk.*
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatOutdatedCronjob
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.PDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.oppfolgingstilfelle.toOffsetDatetime
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.Future

class DialogmotekandidatOutdatedCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val cutoff = LocalDate.now()
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = mockk(),
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )
    private val dialogmotekandidatOutdatedCronjob = DialogmotekandidatOutdatedCronjob(
        outdatedDialogmotekandidatCutoff = cutoff,
        dialogmotekandidatService = dialogmotekandidatService,
    )

    private fun getDialogmotekandidatEndringer(personIdent: Personident): List<PDialogmotekandidatEndring> =
        database.connection.use { connection -> connection.getDialogmotekandidatEndringListForPerson(personIdent) }

    @BeforeEach
    fun setup() {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @Test
    fun `creates LUKKET endring for kandidat before cutoff with no other endringer`() {
        val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(kandidatBeforeCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }
        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(false, kafkaDialogmoteKandidatEndring.kandidat)

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(2, dialogmoteKandidatEndringer.size)
        val latestEndring = dialogmoteKandidatEndringer.first()
        assertEquals(DialogmotekandidatEndringArsak.LUKKET.name, latestEndring.arsak)
        assertEquals(false, latestEndring.kandidat)
    }

    @Test
    fun `updates persons kandidat before cutoff-date only once`() {
        val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime()
        )
        val otherKandidatBeforeCutoff =
            generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT).copy(
                createdAt = cutoff.minusDays(100).toOffsetDatetime()
            )
        val notKandidatBeforeCutoff =
            generateDialogmotekandidatEndringFerdigstilt(UserConstants.ARBEIDSTAKER_2_PERSONIDENTNUMBER).copy(
                createdAt = cutoff.minusDays(50).toOffsetDatetime()
            )
        val kandidatAfterCutoff =
            generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER).copy(
                createdAt = cutoff.plusDays(1).toOffsetDatetime()
            )
        listOf(
            kandidatBeforeCutoff,
            otherKandidatBeforeCutoff,
            notKandidatBeforeCutoff,
            kandidatAfterCutoff
        ).forEach {
            database.createDialogmotekandidatEndring(it)
        }

        var result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)

        result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person with no DialogmotekandidatEndring`() {
        dialogmotekandidatOutdatedCronjob.runJob()

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(0, dialogmoteKandidatEndringer.size)
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person not kandidat before cutoff-date`() {
        val notKandidatBeforeCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(notKandidatBeforeCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(1, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person not kandidat after cutoff-date`() {
        val notKandidatAfterCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
            createdAt = cutoff.plusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(notKandidatAfterCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(1, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person kandidat after cutoff-date`() {
        val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.plusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(kandidatAfterCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(1, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person kandidat before cutoff-date but not kandidat after cutoff-date`() {
        val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime()
        )
        val notKandidatAfterCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
            createdAt = cutoff.plusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(kandidatBeforeCutoff)
        database.createDialogmotekandidatEndring(notKandidatAfterCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(2, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person not kandidat before cutoff-date but kandidat after cutoff-date`() {
        val notKandidatBeforeCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime(),
        )
        val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.plusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(notKandidatBeforeCutoff)
        database.createDialogmotekandidatEndring(kandidatAfterCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(2, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }

    @Test
    fun `creates no DialogmotekandidatEndring for person kandidat before cutoff-date but also kandidat after cutoff-date`() {
        val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.minusDays(1).toOffsetDatetime()
        )
        val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
            createdAt = cutoff.plusDays(1).toOffsetDatetime()
        )
        database.createDialogmotekandidatEndring(kandidatBeforeCutoff)
        database.createDialogmotekandidatEndring(kandidatAfterCutoff)

        val result = dialogmotekandidatOutdatedCronjob.runJob()
        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
        assertEquals(2, dialogmoteKandidatEndringer.size)
        assertTrue(dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name })
    }
}
