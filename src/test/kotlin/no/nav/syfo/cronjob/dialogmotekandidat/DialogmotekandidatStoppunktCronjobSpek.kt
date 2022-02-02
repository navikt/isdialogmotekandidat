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

val kandidatEnPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
val kandidatToPersonIdent = PersonIdentNumber(kandidatEnPersonIdent.value.replace("2", "8"))
val kandidatTrePersonIdent = PersonIdentNumber(kandidatEnPersonIdent.value.replace("3", "7"))

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

        describe("${DialogmotekandidatStoppunktCronjob::class.java.simpleName}: run job") {
            it("Oppdaterer status på dialogmote-kandidat-stoppunkt planlagt i dag med oppfolgingstilfelle") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatEnPersonIdent,
                    planlagt = LocalDate.now(),
                )
                val annetStoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatToPersonIdent,
                    planlagt = LocalDate.now(),
                )
                val stoppunktPlanlagtIMorgen = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatTrePersonIdent,
                    planlagt = LocalDate.now().plusDays(1),
                )
                listOf(
                    stoppunktPlanlagtIDag,
                    annetStoppunktPlanlagtIDag,
                    stoppunktPlanlagtIMorgen
                ).forEach { createDialogmotekandidatStoppunkt(it) }

                val oppfolgingstilfelleKandidatEn = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatEnPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                )
                val oppfolgingstilfelleIkkeKandidatTo = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatToPersonIdent,
                    oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
                )
                val oppfolgingstilfelleKandidatTre = generateOppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = kandidatTrePersonIdent,
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

                val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatEnPersonIdent).first()
                val stoppunktKandidatTo = database.getDialogmotekandidatStoppunktList(kandidatToPersonIdent).first()
                val stoppunktKandidatTre = database.getDialogmotekandidatStoppunktList(kandidatTrePersonIdent).first()

                stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.KANDIDAT.name
                stoppunktKandidatTo.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT.name
                stoppunktKandidatTre.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
                stoppunktKandidatEn.processedAt.shouldNotBeNull()
                stoppunktKandidatTo.processedAt.shouldNotBeNull()
                stoppunktKandidatTre.processedAt.shouldBeNull()
            }
            it("Oppdaterer ikke status på dialogmote-kandidat-stoppunkt planlagt i dag uten oppfolgingstilfelle") {
                val stoppunktPlanlagtIDag = generateDialogmotekandidatStoppunktPlanlagt(
                    arbeidstakerPersonIdent = kandidatEnPersonIdent,
                    planlagt = LocalDate.now(),
                )
                createDialogmotekandidatStoppunkt(stoppunktPlanlagtIDag)

                val result = dialogmotekandidatStoppunktCronjob.runJob()
                result.failed shouldBeEqualTo 1
                result.updated shouldBeEqualTo 0

                val stoppunktKandidatEn = database.getDialogmotekandidatStoppunktList(kandidatEnPersonIdent).first()

                stoppunktKandidatEn.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT.name
                stoppunktKandidatEn.processedAt.shouldBeNull()
            }
        }
    }
})
