package no.nav.syfo.cronjob.dialogmotekandidat

import io.ktor.server.testing.*
import no.nav.syfo.dialogmotekandidat.*
import no.nav.syfo.dialogmotekandidat.database.createDialogmotekandidatStoppunkt
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatStoppunktList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.*
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatStoppunktPlanlagt
import no.nav.syfo.testhelper.generator.generateOppfolgingstilfelleArbeidstaker
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class DialogmotekandidatStoppunktCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database
        )
        val dialogmotekandidatService = DialogmotekandidatService(
            oppfolgingstilfelleService = oppfolgingstilfelleService,
            database = database,
        )
        val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
            dialogmotekandidatService = dialogmotekandidatService
        )

        beforeEachTest {
            database.dropData()
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
            it("Do not update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker exists for person") {
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

                val stoppunktKandidatFirst = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()
                val stoppunktKandidatSecond = database.getDialogmotekandidatStoppunktList(kandidatSecondPersonIdent).first()
                val stoppunktKandidatThird = database.getDialogmotekandidatStoppunktList(kandidatThirdPersonIdent).first()

                stoppunktKandidatFirst.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
                stoppunktKandidatSecond.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
                stoppunktKandidatThird.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
                stoppunktKandidatFirst.processedAt.shouldNotBeNull()
                stoppunktKandidatSecond.processedAt.shouldNotBeNull()
                stoppunktKandidatThird.processedAt.shouldBeNull()
            }
            it("Do not update status of DialogmotekandidatStoppunkt, if planlagt is today and OppfolgingstilfelleArbeidstaker exists for person") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatFirstPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

                val result = dialogmotekandidatStoppunktCronjob.runJob()
                result.failed shouldBeEqualTo 1
                result.updated shouldBeEqualTo 0

                val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatFirstPersonIdent).first()

                stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
                stoppunktKandidatEn.processedAt.shouldBeNull()
            }
        }
    }
})
