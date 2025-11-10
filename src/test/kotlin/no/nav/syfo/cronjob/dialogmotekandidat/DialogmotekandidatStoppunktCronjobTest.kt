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
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Future

class DialogmotekandidatStoppunktCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )
    private val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )
    private val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
        dialogmotekandidatService = dialogmotekandidatService,
        externalMockEnvironment.environment.stoppunktCronjobDelay,
    )

    @BeforeEach
    fun setup() {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    private fun createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt) {
        database.connection.createDialogmotekandidatStoppunkt(true, dialogmotekandidatStoppunkt)
    }

    private fun createDialogmoteStatus(dialogmoteStatusEndring: DialogmoteStatusEndring) {
        database.connection.createDialogmoteStatus(true, dialogmoteStatusEndring)
    }

    private val kandidatFirstPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val kandidatSecondPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
    private val kandidatThirdPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        val annetStoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatSecondPersonIdent,
            planlagt = LocalDate.now(),
        )
        val stoppunktPlanlagtIMorgen = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatThirdPersonIdent,
            planlagt = LocalDate.now().plusDays(1),
        )
        listOf(
            stoppunktPlanlagtIDag,
            annetStoppunktPlanlagtIDag,
            stoppunktPlanlagtIMorgen
        ).forEach { createDialogmotekandidatStoppunkt(it) }

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val stoppunktKandidatFirst =
            database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()
        val stoppunktKandidatSecond =
            database.getDialogmotekandidatStoppunktList(kandidatSecondPersonIdent).first()
        val stoppunktKandidatThird =
            database.getDialogmotekandidatStoppunktList(kandidatThirdPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatFirst.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatSecond.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, stoppunktKandidatThird.status)
        assertNotNull(stoppunktKandidatFirst.processedAt)
        assertNotNull(stoppunktKandidatSecond.processedAt)
        assertNull(stoppunktKandidatThird.processedAt)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(kandidatFirstPersonIdent.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(true, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaDialogmoteKandidatEndring.tilfelleStart)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is yesterday and OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtYesterday = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now().minusDays(1),
        )
        val stoppunktPlanlagtToday = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatSecondPersonIdent,
            planlagt = LocalDate.now(),
        )
        val stoppunktPlanlagtTomorrow = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatThirdPersonIdent,
            planlagt = LocalDate.now().plusDays(1),
        )
        listOf(
            stoppunktPlanlagtYesterday,
            stoppunktPlanlagtToday,
            stoppunktPlanlagtTomorrow
        ).forEach { createDialogmotekandidatStoppunkt(it) }

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(2, result.updated)
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val stoppunktKandidatFirst =
            database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()
        val stoppunktKandidatSecond =
            database.getDialogmotekandidatStoppunktList(kandidatSecondPersonIdent).first()
        val stoppunktKandidatThird =
            database.getDialogmotekandidatStoppunktList(kandidatThirdPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatFirst.status)
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatSecond.status)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name, stoppunktKandidatThird.status)
        assertNotNull(stoppunktKandidatFirst.processedAt)
        assertNotNull(stoppunktKandidatSecond.processedAt)
        assertNull(stoppunktKandidatThird.processedAt)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(kandidatFirstPersonIdent.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(true, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaDialogmoteKandidatEndring.tilfelleStart)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker with dodsdato exists for person`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }
        val stoppunktKandidatFirst =
            database.getDialogmotekandidatStoppunktList(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD)
                .first()
        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatFirst.status)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt and handles duplicate stoppunktPlanlagt`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)
        (1..9).map {
            stoppunktPlanlagtIDag.copy(uuid = UUID.randomUUID())
        }.forEach { createDialogmotekandidatStoppunkt(it) }

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(10, result.updated)
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val stoppunktList = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent)
        assertEquals(10, stoppunktList.size)
        var kandidatCounter = 0
        (0..9).forEach {
            val stoppunktKandidat = stoppunktList[it]
            if (stoppunktKandidat.status == DialogmotekandidatStoppunktStatus.KANDIDAT.name) kandidatCounter++
            assertNotNull(stoppunktKandidat.processedAt)
        }
        assertEquals(1, kandidatCounter)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(kandidatFirstPersonIdent.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(true, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaDialogmoteKandidatEndring.tilfelleStart)
    }

    @Test
    fun `Update status of DialogmotekandidatStoppunkt, if planlagt is today and no OppfolgingstilfelleArbeidstaker exists for person`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatSecondPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
        assertEquals(0, result.failed)
        assertEquals(1, result.updated)
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatEn.status)
        assertNotNull(stoppunktKandidatEn.processedAt)
    }

    @Test
    fun `Updates status to KANDIDAT and creates endring when person is not currently Kandidat`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        verify(exactly = 1) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatEn.status)
        assertNotNull(stoppunktKandidatEn.processedAt)

        val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
            personIdent = kandidatFirstPersonIdent
        ).firstOrNull()

        assertNotNull(latestDialogmotekandidatEndring)
        assertEquals(true, latestDialogmotekandidatEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, latestDialogmotekandidatEndring.arsak)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT when meeting already ferdigstilt within oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)
        createDialogmoteStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirstPersonIdent,
                    statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
                    moteTidspunkt = OffsetDateTime.now()
                        .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
                    endringsTidspunkt = OffsetDateTime.now()
                        .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 11),
                )
            )
        )

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidat = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidat.status)
        assertNotNull(stoppunktKandidat.processedAt)

        val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
            personIdent = kandidatFirstPersonIdent
        ).firstOrNull()

        assertNull(latestDialogmotekandidatEndring)
    }

    @Test
    fun `Updates status to KANDIDAT when ferdigstilt meeting is before oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)
        createDialogmoteStatus(
            DialogmoteStatusEndring.create(
                generateKDialogmoteStatusEndring(
                    personIdentNumber = kandidatFirstPersonIdent,
                    statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
                    moteTidspunkt = OffsetDateTime.now()
                        .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 10),
                    endringsTidspunkt = OffsetDateTime.now()
                        .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 9),
                )
            )
        )

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val stoppunktKandidat = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidat.status)
        assertNotNull(stoppunktKandidat.processedAt)

        val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
            personIdent = kandidatFirstPersonIdent
        ).firstOrNull()

        assertNotNull(latestDialogmotekandidatEndring)
        assertEquals(true, latestDialogmotekandidatEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, latestDialogmotekandidatEndring.arsak)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(kandidatFirstPersonIdent.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(true, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
        assertEquals(LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), kafkaDialogmoteKandidatEndring.tilfelleStart)
    }

    @Test
    fun `Updates status to KANDIDAT and creates new endring when person was Kandidat before current Oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = kandidatFirstPersonIdent,
        ).copy(
            createdAt = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringStoppunkt)

        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
            personIdentNumber = kandidatFirstPersonIdent,
        ).copy(
            createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringFerdigstilt)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        verify(exactly = 1) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.KANDIDAT.name, stoppunktKandidatEn.status)
        assertNotNull(stoppunktKandidatEn.processedAt)

        val dialogmotekandidatEndringList = database.connection.getDialogmotekandidatEndringListForPerson(
            personIdent = kandidatFirstPersonIdent
        )
        assertEquals(3, dialogmotekandidatEndringList.size)

        val firstDialogmotekandidatEndring = dialogmotekandidatEndringList[2]
        assertNotNull(firstDialogmotekandidatEndring)
        assertEquals(true, firstDialogmotekandidatEndring.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, firstDialogmotekandidatEndring.arsak)

        val secondDialogmotekandidatEndring = dialogmotekandidatEndringList[1]
        assertNotNull(secondDialogmotekandidatEndring)
        assertEquals(false, secondDialogmotekandidatEndring.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, secondDialogmotekandidatEndring.arsak)

        val lastDialogmotekandidatEndring = dialogmotekandidatEndringList[0]
        assertNotNull(lastDialogmotekandidatEndring)
        assertEquals(true, lastDialogmotekandidatEndring.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, lastDialogmotekandidatEndring.arsak)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT with no new endring when person is Kandidat in current Oppfolgingstilfelle`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = kandidatFirstPersonIdent,
        ).copy(
            createdAt = LocalDate.now().minusDays(1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatEn.status)
        assertNotNull(stoppunktKandidatEn.processedAt)

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            ).firstOrNull()

        assertEquals(dialogmotekandidatEndring.uuid, latestDialogmotekandidatEndring?.uuid)
    }

    @Test
    fun `Updates status to IKKE_KANDIDAT with no new endring when person is already Kandidat`() {
        val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
            arbeidstakerPersonIdent = kandidatFirstPersonIdent,
            planlagt = LocalDate.now(),
        )
        createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = kandidatFirstPersonIdent,
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)

        val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

        assertEquals(1, result.updated)
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

        assertEquals(DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name, stoppunktKandidatEn.status)
        assertNotNull(stoppunktKandidatEn.processedAt)

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            ).firstOrNull()

        assertEquals(dialogmotekandidatEndring.uuid, latestDialogmotekandidatEndring?.uuid)
    }
}
