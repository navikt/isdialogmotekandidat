package no.nav.syfo.cronjob.dialogmotekandidat

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.defaultZoneOffset
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class DialogmotekandidatStoppunktCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database
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

            clearMocks(dialogmotekandidatEndringProducer)
            justRun { dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any()) }
        }

        fun createOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker) {
            database.connection.createOppfolgingstilfelleArbeidstaker(true, oppfolgingstilfelleArbeidstaker)
        }

        fun createDialogmotekandidatStoppunkt(dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt) {
            database.connection.createDialogmotekandidatStoppunkt(true, dialogmotekandidatStoppunkt)
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

                val result = dialogmotekandidatStoppunktCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 2
                verify(exactly = 1) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
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
            }
            it("Do not update status of DialogmotekandidatStoppunkt, if planlagt is today and no OppfolgingstilfelleArbeidstaker exists for person") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

                val result = dialogmotekandidatStoppunktCronjob.runJob()
                result.failed shouldBeEqualTo 1
                result.updated shouldBeEqualTo 0
                verify(exactly = 0) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                }

                val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

                stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
                stoppunktKandidatEn.processedAt.shouldBeNull()
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

                val result = dialogmotekandidatStoppunktCronjob.runJob()

                result.updated shouldBeEqualTo 1
                verify(exactly = 1) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
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

                val result = dialogmotekandidatStoppunktCronjob.runJob()

                result.updated shouldBeEqualTo 1
                verify(exactly = 1) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
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

                val result = dialogmotekandidatStoppunktCronjob.runJob()

                result.updated shouldBeEqualTo 1
                verify(exactly = 0) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
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

                val result = dialogmotekandidatStoppunktCronjob.runJob()

                result.updated shouldBeEqualTo 1
                verify(exactly = 0) {
                    dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
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
