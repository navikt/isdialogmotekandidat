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
import org.junit.jupiter.api.DisplayName
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
    private fun createStatus(dialogmoteStatusEndring: DialogmoteStatusEndring) = database.connection.createDialogmoteStatus(true, dialogmoteStatusEndring)

    private val kandidatFirstPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val kandidatSecondPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
    private val kandidatThirdPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        val annetStoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecondPersonident, LocalDate.now())
        val stoppunktPlanlagtIMorgen = generateDialogmotekandidatStoppunktPlanlagt(kandidatThirdPersonident, LocalDate.now().plusDays(1))
        listOf(stoppunktPlanlagtIDag, annetStoppunktPlanlagtIDag, stoppunktPlanlagtIMorgen).forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val stoppunktKandidatFirst = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        val stoppunktKandidatSecond = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonident).first()
        val stoppunktKandidatThird = database.getDialogmotekandidatStoppunktList(kandidatThirdPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatFirst.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatSecond.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, stoppunktKandidatThird.status)
        assertNotNull(stoppunktKandidatFirst.processedAt)
        assertNotNull(stoppunktKandidatSecond.processedAt)
        assertNull(stoppunktKandidatThird.processedAt)
        val kafkaValue = slotRecord.captured.value()
        assertEquals(kandidatFirstPersonident.value, kafkaValue.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaValue.arsak)
        assertTrue(kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaValue.tilfelleStart)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is yesterday and OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtYesterday = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now().minusDays(1))
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecondPersonident, LocalDate.now())
        val stoppunktPlanlagtIMorgen = generateDialogmotekandidatStoppunktPlanlagt(kandidatThirdPersonident, LocalDate.now().plusDays(1))
        listOf(stoppunktPlanlagtYesterday, stoppunktPlanlagtIDag, stoppunktPlanlagtIMorgen).forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val stoppunktKandidatFirst = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        val stoppunktKandidatSecond = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonident).first()
        val stoppunktKandidatThird = database.getDialogmotekandidatStoppunktList(kandidatThirdPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatFirst.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatSecond.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, stoppunktKandidatThird.status)
        assertNotNull(stoppunktKandidatFirst.processedAt)
        assertNotNull(stoppunktKandidatSecond.processedAt)
        assertNull(stoppunktKandidatThird.processedAt)
        val kafkaValue = slotRecord.captured.value()
        assertEquals(kandidatFirstPersonident.value, kafkaValue.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaValue.arsak)
        assertTrue(kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaValue.tilfelleStart)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker with dodsdato exists for person`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunktKandidatFirst = database.getDialogmotekandidatStoppunktList(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatFirst.status)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt and handles duplicate stoppunktPlanlagt`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        (1..9).map { stoppunktPlanlagtIDag.copy(uuid = UUID.randomUUID()) }.forEach { createStoppunkt(it) }
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(10, result.updated)

        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val list = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident)
        assertEquals(10, list.size)

        val kandidatCount = list.count { it.status == DialogmotekandidatStoppunktStatus.KANDIDAT.name }
        assertEquals(1, kandidatCount)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, slotRecord.captured.value().arsak)

        val kafkaDialogmoteKandidatEndring = slotRecord.captured.value()
        assertEquals(kandidatFirstPersonident.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertTrue(kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(
            LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
            kafkaDialogmoteKandidatEndring.tilfelleStart,
        )
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and no OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtIDagSecond = generateDialogmotekandidatStoppunktPlanlagt(kandidatSecondPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDagSecond)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunktKandidat = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidat.status)
        assertNotNull(stoppunktKandidat.processedAt)
    }

    @Test
    fun `Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        val slotRecord = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirstPersonident).firstOrNull()
        assertNotNull(latestEndring)
        assertTrue(latestEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, latestEndring.arsak)
    }

    @Test
    fun `Updates status of DialogmotekandidatStoppunkt to IKKE_KANDIDAT for DialogmotekandidatStoppunkt planlagt today when meeting already ferdigstilt within oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        createStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirstPersonident,
                    statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
                    moteTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
                    endringsTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 11),
                )
            )
        )
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirstPersonident).firstOrNull()
        assertNull(latestEndring)
    }

    @Test
    fun `Updates status of DialogmotekandidatStoppunkt to KANDIDAT for DialogmotekandidatStoppunkt planlagt today when already ferdigstilt mote is before oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        createStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirstPersonident,
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
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val list = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirstPersonident)
        assertEquals(1, list.size)
        val firstEndring = list[0]
        assertTrue(firstEndring.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, firstEndring.arsak)

        val kafkaDialogmoteKandidatEndring = slotRecord.captured.value()
        assertEquals(kandidatFirstPersonident.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertTrue(kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(
            LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
            kafkaDialogmoteKandidatEndring.tilfelleStart,
        )
    }

    @Test
    @DisplayName("Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat, and has been Kandidat with DialogmotekandidatEndringArsak Stoppunkt before start of latest Oppfolgingstilfelle")
    fun `Updates status to KANDIDAT and creates new endring when previously kandidat then ferdigstilt then today planlagt`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(kandidatFirstPersonident).copy(
            createdAt = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        database.createDialogmotekandidatEndring(stoppunktEndring)
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(kandidatFirstPersonident).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        database.createDialogmotekandidatEndring(ferdigstiltEndring)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 1) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val list = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirstPersonident)
        assertEquals(3, list.size)
        val stoppunktKandidatFirst = list[2]
        assertTrue(stoppunktKandidatFirst.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, stoppunktKandidatFirst.arsak)
        val stoppunktKandidatSecond = list[1]
        assertFalse(stoppunktKandidatSecond.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, stoppunktKandidatSecond.arsak)
        val stoppunktKandidatThird = list[0]
        assertTrue(stoppunktKandidatThird.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, stoppunktKandidatThird.arsak)
    }

    @Test
    @DisplayName("Updates status of DialogmotekandidatStoppunkt to IKKE_KANDIDAT and creates no new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is Kandidat")
    fun `Updates status to IKKE_KANDIDAT when latest endring is stopppunkt kandidat inside oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(kandidatFirstPersonident, LocalDate.now())
        createStoppunkt(stoppunktPlanlagtIDag)
        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(kandidatFirstPersonident)
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring)
        val result = runBlocking { cronjob.runJob() }
        assertEquals(1, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val stoppunkt = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonident).first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunkt.status)
        assertNotNull(stoppunkt.processedAt)
        val latestEndring = database.connection.getDialogmotekandidatEndringListForPerson(kandidatFirstPersonident).firstOrNull()
        assertEquals(dialogmotekandidatEndring.uuid, latestEndring?.uuid)
    }
}
