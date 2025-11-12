package no.nav.syfo.cronjob.dialogmotekandidat

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatStoppunktPlanlagt
import no.nav.syfo.testhelper.generator.generateKDialogmoteStatusEndring
import no.nav.syfo.util.defaultZoneOffset
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatStoppunktCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val endringProducer = DialogmotekandidatEndringProducer(kafkaProducerDialogmotekandidatEndring = kafkaProducer)
    private val azureAdClient = AzureAdClient(externalMockEnvironment.environment.azure, externalMockEnvironment.mockHttpClient)
    private val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfelleClient)
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = endringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )
    private val cronjob = DialogmotekandidatStoppunktCronjob(dialogmotekandidatService, externalMockEnvironment.environment.stoppunktCronjobDelay)

    @BeforeEach
    fun setup() {
        database.dropData()
        clearMocks(kafkaProducer)
        every { kafkaProducer.send(any()) } returns mockk(relaxed = true)
    }

    private fun createStoppunkt(stoppunkt: DialogmotekandidatStoppunkt) = database.connection.createDialogmotekandidatStoppunkt(true, stoppunkt)
    private fun createStatus(status: DialogmoteStatusEndring) = database.connection.createDialogmoteStatus(true, status)

    private val kandidatFirst = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val kandidatSecond = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
    private val kandidatThird = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT

    @Test
    fun `Update status of stoppunkt if planlagt is today`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        val sSecondToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecond, LocalDate.now())
        val sTomorrow = generateDialogmotekandidatStoppunktPlanlagt(kandidatThird, LocalDate.now().plusDays(1))
        listOf(sToday, sSecondToday, sTomorrow).forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val first = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        val second = database.getDialogmotekandidatStoppunktList(kandidatSecond).first()
        val third = database.getDialogmotekandidatStoppunktList(kandidatThird).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, first.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, second.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, third.status)
        assertNotNull(first.processedAt)
        assertNotNull(second.processedAt)
        assertNull(third.processedAt)
        val kafkaValue = slotRecord.captured.value()
        assertEquals(kandidatFirst.value, kafkaValue.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaValue.arsak)
        assertEquals(true, kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaValue.tilfelleStart)
    }

    @Test
    fun `Update status of stoppunkt if planlagt is yesterday`() {
        val sYesterday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now().minusDays(1))
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecond, LocalDate.now())
        val sTomorrow = generateDialogmotekandidatStoppunktPlanlagt(kandidatThird, LocalDate.now().plusDays(1))
        listOf(sYesterday, sToday, sTomorrow).forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val first = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        val second = database.getDialogmotekandidatStoppunktList(kandidatSecond).first()
        val third = database.getDialogmotekandidatStoppunktList(kandidatThird).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, first.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, second.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, third.status)
        assertNotNull(first.processedAt)
        assertNotNull(second.processedAt)
        assertNull(third.processedAt)
        val kafkaValue = slotRecord.captured.value()
        assertEquals(kandidatFirst.value, kafkaValue.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaValue.arsak)
        assertTrue(kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaValue.tilfelleStart)
    }

    @Test
    fun `Update status for person with dodsdato`() {
        val sTodayDod = generateDialogmotekandidatStoppunktPlanlagt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD, LocalDate.now())
        createStoppunkt(sTodayDod)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val first = database.getDialogmotekandidatStoppunktList(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, first.status)
    }

    @Test
    fun `Update status handles duplicate stoppunktPlanlagt`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        (1..9).map { sToday.copy(uuid = UUID.randomUUID()) }.forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(10, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val list = database.getDialogmotekandidatStoppunktList(kandidatFirst)
        assertEquals(10, list.size)
        val kandidatCount = list.count { it.status == DialogmotekandidatStoppunktStatus.KANDIDAT.name }
        assertEquals(1, kandidatCount)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, slotRecord.captured.value().arsak)
    }

    @Test
    fun `Update status planlagt today with no oppfolgingstilfelle`() {
        val sTodaySecond = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecond, LocalDate.now())
        createStoppunkt(sTodaySecond)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val en = database.getDialogmotekandidatStoppunktList(kandidatSecond).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, en.status)
        assertNotNull(en.processedAt)
    }

    @Test
    fun `Updates status to KANDIDAT and creates endring when latest endring for person not kandidat`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst).firstOrNull()
        assertNotNull(latestEndring)
        assertTrue(latestEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, latestEndring.arsak)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT when meeting ferdigstilt within oppfolgingstilfelle`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        createStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirst,
                    statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
                    moteTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
                    endringsTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 11),
                )
            )
        )
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst).firstOrNull()
        assertNull(latestEndring)
    }

    @Test
    fun `Updates status to KANDIDAT when ferdigstilt mote before oppfolgingstilfelle`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        createStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirst,
                    statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
                    moteTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 10),
                    endringsTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 9),
                )
            )
        )
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val list = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst)
        assertEquals(1, list.size)
        val firstEndring = list[0]
        assertTrue(firstEndring.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, firstEndring.arsak)
    }

    @Test
    fun `Updates status to KANDIDAT and creates new endring when previously kandidat then ferdigstilt then today planlagt`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(kandidatFirst).copy(
            createdAt = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        database.createDialogmotekandidatEndring(stoppunktEndring)
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(kandidatFirst).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        database.createDialogmotekandidatEndring(ferdigstiltEndring)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 1) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val list = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst)
        assertEquals(3, list.size)
        val first = list[2]
        assertTrue(first.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, first.arsak)
        val second = list[1]
        assertFalse(second.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, second.arsak)
        val third = list[0]
        assertTrue(third.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, third.arsak)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT when latest endring is stopppunkt kandidat inside oppfolgingstilfelle`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        val endring = generateDialogmotekandidatEndringStoppunkt(kandidatFirst).copy(createdAt = LocalDate.now().minusDays(1).atStartOfDay().atOffset(defaultZoneOffset))
        database.createDialogmotekandidatEndring(endring)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst).firstOrNull()
        assertEquals(endring.uuid, latestEndring?.uuid)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT when latest endring is stoppunkt kandidat today`() {
        val sToday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirst, LocalDate.now())
        createStoppunkt(sToday)
        val endring = generateDialogmotekandidatEndringStoppunkt(kandidatFirst)
        database.createDialogmotekandidatEndring(endring)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirst).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirst).firstOrNull()
        assertEquals(endring.uuid, latestEndring?.uuid)
    }
}
