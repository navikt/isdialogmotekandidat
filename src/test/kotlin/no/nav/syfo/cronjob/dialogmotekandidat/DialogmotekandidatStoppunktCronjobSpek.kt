package no.nav.syfo.cronjob.dialogmotekandidat

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.dialogmotestatusendring.database.createDialogmoteStatus
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusEndring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusEndringType
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.defaultZoneOffset
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Future

class DialogmotekandidatStoppunktCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
        val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
            kafkaProducerDialogmotekandidatEndring = kafkaProducer,
        )
        val azureAdClient = AzureAdClient(
            azureEnvironment = externalMockEnvironment.environment.azure,
        )
        val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
            azureAdClient = azureAdClient,
            clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        )
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = externalMockEnvironment.database,
            oppfolgingstilfelleClient = oppfolgingstilfelleClient,
            readFromIsoppfolgingstilfelleEnabled = externalMockEnvironment.environment.readFromIsoppfolgingstilfelleEnabled,
        )
        val dialogmotekandidatService = DialogmotekandidatService(
            oppfolgingstilfelleService = oppfolgingstilfelleService,
            dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            database = database,
        )
        val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
            dialogmotekandidatService = dialogmotekandidatService
        )

        beforeEachTest {
            database.dropData()

            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        fun createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker) {
            database.connection.createOppfolgingstilfelleArbeidstaker(true, oppfolgingstilfelleArbeidstaker)
        }

        fun createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt) {
            database.connection.createDialogmotekandidatStoppunkt(true, dialogmotekandidatStoppunkt)
        }

        fun createDialogmoteStatus(dialogmoteStatusEndring: DialogmoteStatusEndring) {
            database.connection.createDialogmoteStatus(true, dialogmoteStatusEndring)
        }

        val kandidatFirstPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
        val kandidatSecondPersonIdent = PersonIdentNumber(kandidatFirstPersonIdent.value.replace("2", "8"))
        val kandidatThirdPersonIdent = PersonIdentNumber(kandidatFirstPersonIdent.value.replace("3", "7"))

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

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                val oppfolgingstilfelleIkkeKandidatTo = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatSecondPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
                )
                val oppfolgingstilfelleKandidatTre = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatThirdPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
                )
                listOf(
                    oppfolgingstilfelleKandidatEn,
                    oppfolgingstilfelleIkkeKandidatTo,
                    oppfolgingstilfelleKandidatTre
                ).forEach { createOppfolgingstilfelle(it) }

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
                kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo oppfolgingstilfelleKandidatEn.tilfelleStart
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

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                val oppfolgingstilfelleIkkeKandidatTo = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatSecondPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
                )
                val oppfolgingstilfelleKandidatTre = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatThirdPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
                )
                listOf(
                    oppfolgingstilfelleKandidatEn,
                    oppfolgingstilfelleIkkeKandidatTo,
                    oppfolgingstilfelleKandidatTre
                ).forEach { createOppfolgingstilfelle(it) }

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
                kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo oppfolgingstilfelleKandidatEn.tilfelleStart
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

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleKandidatEn)

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
                kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo oppfolgingstilfelleKandidatEn.tilfelleStart
            }
            it("Update status of DialogmotekandidatStoppunkt, if planlagt is today and no OppfolgingstilfelleArbeidstaker exists for person") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

                val result = runBlocking { dialogmotekandidatStoppunktCronjob.runJob() }
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1
                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

                stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
                stoppunktKandidatEn.processedAt.shouldNotBeNull()
            }
            it("Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

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
                            moteTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
                            endringsTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 11),
                        )
                    )
                )

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

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
                            moteTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 10),
                            endringsTidspunkt = OffsetDateTime.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 9),
                        )
                    )
                )

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

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
                kafkaDialogmoteKandidatEndring.tilfelleStart shouldBeEqualTo oppfolgingstilfelleKandidatEn.tilfelleStart
            }
            it("Updates status of DialogmotekandidatStoppunkt to KANDIDAT and creates new DialogmotekandidatEndring for DialogmotekandidatStoppunkt planlagt today when latest endring for person is not Kandidat, and has been Kandidat with DialogmotekandidatEndringArsak Stoppunkt before start of latest Oppfolgingstilfelle") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt = stoppunktPlanlagtIDag)

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

                val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                    personIdentNumber = kandidatFirstPersonIdent,
                ).copy(
                    createdAt = oppfolgingstilfelleKandidatEn.tilfelleStart.minusDays(1).atStartOfDay()
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

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

                val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                    personIdentNumber = kandidatFirstPersonIdent,
                ).copy(
                    createdAt = oppfolgingstilfelleKandidatEn.tilfelleEnd.minusDays(1).atStartOfDay()
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

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleKandidatEn)

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
    }
})
