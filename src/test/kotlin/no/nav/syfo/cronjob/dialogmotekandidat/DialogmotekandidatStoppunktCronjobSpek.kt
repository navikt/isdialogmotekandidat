package no.nav.syfo.cronjob.dialogmotekandidat

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatStoppunktList
import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatStoppunktPlanlagt
import no.nav.syfo.testhelper.generator.generateKDialogmoteStatusEndring
import no.nav.syfo.util.defaultZoneOffset
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Future

class DialogmotekandidatStoppunktCronjobSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )
    val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
        dialogmotekandidatService = dialogmotekandidatService,
        externalMockEnvironment.environment.stoppunktCronjobDelay,
    )

    beforeEachTest {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    fun createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt) {
        database.connection.createDialogmotekandidatStoppunkt(true, dialogmotekandidatStoppunkt)
    }

    fun createDialogmoteStatus(dialogmoteStatusEndring: DialogmoteStatusEndring) {
        database.connection.createDialogmoteStatus(true, dialogmoteStatusEndring)
    }

    val kandidatFirstPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    val kandidatSecondPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
    val kandidatThirdPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT

    describe("${DialogmotekandidatStoppunktCronjob::class.java.simpleName}: run job") {
        it("Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker exists for person") {
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
            result.failed shouldBeEqualTo 0
            result.updated shouldBeEqualTo 2
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

            stoppunktKandidatFirst.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
            stoppunktKandidatSecond.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidatThird.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
            stoppunktKandidatFirst.processedAt.shouldNotBeNull()
            stoppunktKandidatSecond.processedAt.shouldNotBeNull()
            stoppunktKandidatThird.processedAt.shouldBeNull()

            val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
            kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo kandidatFirstPersonIdent.value
            kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
            kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo true
            kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo LocalDate.now()
                .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        }
        it("Update status of DialogmotekandidatStoppunkt, if planlagt is yesterday and OppfolgingstilfelleArbeidstaker exists for person") {
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
            result.failed shouldBeEqualTo 0
            result.updated shouldBeEqualTo 2
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

            stoppunktKandidatFirst.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
            stoppunktKandidatSecond.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidatThird.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
            stoppunktKandidatFirst.processedAt.shouldNotBeNull()
            stoppunktKandidatSecond.processedAt.shouldNotBeNull()
            stoppunktKandidatThird.processedAt.shouldBeNull()

            val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
            kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo kandidatFirstPersonIdent.value
            kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
            kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo true
            kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo LocalDate.now()
                .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        }
        it("Update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker with dodsdato exists for person") {
            val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD,
                planlagt = LocalDate.now(),
            )
            createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

            val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
            result.failed shouldBeEqualTo 0
            result.updated shouldBeEqualTo 1
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }
            val stoppunktKandidatFirst =
                database.getDialogmotekandidatStoppunktList(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD)
                    .first()
            stoppunktKandidatFirst.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
        }
        it("Update status of DialogmotekandidatStoppunkt and handles duplicate stoppunktPlanlagt") {
            val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                planlagt = LocalDate.now(),
            )
            createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)
            (1..9).map {
                stoppunktPlanlagtIDag.copy(uuid = UUID.randomUUID())
            }.forEach { createDialogmotekandidatStoppunkt(it) }

            val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
            result.failed shouldBeEqualTo 0
            result.updated shouldBeEqualTo 10
            val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
            verify(exactly = 1) {
                kafkaProducer.send(capture(producerRecordSlot))
            }

            val stoppunktList = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent)
            stoppunktList.size shouldBeEqualTo 10
            var kandidatCounter = 0
            (0..9).forEach {
                val stoppunktKandidat = stoppunktList[it]
                if (stoppunktKandidat.status == DialogmotekandidatStoppunktStatus.KANDIDAT.name) kandidatCounter++
                stoppunktKandidat.processedAt.shouldNotBeNull()
            }
            kandidatCounter shouldBeEqualTo 1

            val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
            kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo kandidatFirstPersonIdent.value
            kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
            kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo true
            kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo LocalDate.now()
                .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        }
        it("Update status of DialogmotekandidatStoppunkt, if planlagt is today and no OppfolgingstilfelleArbeidstaker exists for person") {
            val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                arbeidstakerPersonIdent = kandidatSecondPersonIdent,
                planlagt = LocalDate.now(),
            )
            createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

            val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
            result.failed shouldBeEqualTo 0
            result.updated shouldBeEqualTo 1
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonIdent).first()

            stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidatEn.processedAt.shouldNotBeNull()
        }
        it("Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat") {
            val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                planlagt = LocalDate.now(),
            )
            createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

            val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }

            result.updated shouldBeEqualTo 1
            verify(exactly = 1) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
            stoppunktKandidatEn.processedAt.shouldNotBeNull()

            val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            ).firstOrNull()

            latestDialogmotekandidatEndring.shouldNotBeNull()
            latestDialogmotekandidatEndring.kandidat shouldBeEqualTo true
            latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
        }
        it("Updates status of DialogmotekandidatStoppunkt to IKKE_KANDIDAT for DialogmotekandidatStoppunkt planlagt today when meeting already ferdigstilt within oppfolgingstilfelle") {
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

            result.updated shouldBeEqualTo 1
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidat = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidat.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidat.processedAt.shouldNotBeNull()

            val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            ).firstOrNull()

            latestDialogmotekandidatEndring shouldBe null
        }
        it("Updates status of DialogmotekandidatStoppunkt to KANDIDAT for DialogmotekandidatStoppunkt planlagt today when already ferdigstilt mote is before oppfolgingstilfelle") {
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

            result.updated shouldBeEqualTo 1
            val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
            verify(exactly = 1) {
                kafkaProducer.send(capture(producerRecordSlot))
            }

            val stoppunktKandidat = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidat.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
            stoppunktKandidat.processedAt.shouldNotBeNull()

            val latestDialogmotekandidatEndring = database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            ).firstOrNull()

            latestDialogmotekandidatEndring.shouldNotBeNull()
            latestDialogmotekandidatEndring.kandidat shouldBeEqualTo true
            latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name

            val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
            kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo kandidatFirstPersonIdent.value
            kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
            kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo true
            kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo LocalDate.now()
                .minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        }
        it("Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat, and has been Kandidat with DialogmotekandidatEndringArsak Stoppunkt before start of latest Oppfolgingstilfelle") {
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

            result.updated shouldBeEqualTo 1
            verify(exactly = 1) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
            stoppunktKandidatEn.processedAt.shouldNotBeNull()

            val dialogmotekandidatEndringList = database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = kandidatFirstPersonIdent
            )
            dialogmotekandidatEndringList.size shouldBeEqualTo 3

            val firstDialogmotekandidatEndring = dialogmotekandidatEndringList[2]
            firstDialogmotekandidatEndring.shouldNotBeNull()
            firstDialogmotekandidatEndring.kandidat shouldBeEqualTo true
            firstDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name

            val secondDialogmotekandidatEndring = dialogmotekandidatEndringList[1]
            secondDialogmotekandidatEndring.shouldNotBeNull()
            secondDialogmotekandidatEndring.kandidat shouldBeEqualTo false
            secondDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name

            val lastDialogmotekandidatEndring = dialogmotekandidatEndringList[0]
            lastDialogmotekandidatEndring.shouldNotBeNull()
            lastDialogmotekandidatEndring.kandidat shouldBeEqualTo true
            lastDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
        }

        it("Updates status of DialogmotekandidatStoppunkt to IKKE_KANDIDAT and creates no new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat, but has been Kandidat with DialogmotekandidatEndringArsak Stoppunkt in latest Oppfolgingstilfelle") {
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

            result.updated shouldBeEqualTo 1
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidatEn.processedAt.shouldNotBeNull()

            val latestDialogmotekandidatEndring =
                database.connection.getDialogmotekandidatEndringListForPerson(
                    personIdent = kandidatFirstPersonIdent
                ).firstOrNull()

            latestDialogmotekandidatEndring?.uuid shouldBeEqualTo dialogmotekandidatEndring.uuid
        }

        it("Updates status of DialogmotekandidatStoppunkt to IKKE_KANDIDAT and creates no new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is Kandidat") {
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

            result.updated shouldBeEqualTo 1
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

            stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
            stoppunktKandidatEn.processedAt.shouldNotBeNull()

            val latestDialogmotekandidatEndring =
                database.connection.getDialogmotekandidatEndringListForPerson(
                    personIdent = kandidatFirstPersonIdent
                ).firstOrNull()

            latestDialogmotekandidatEndring?.uuid shouldBeEqualTo dialogmotekandidatEndring.uuid
        }
    }
})
