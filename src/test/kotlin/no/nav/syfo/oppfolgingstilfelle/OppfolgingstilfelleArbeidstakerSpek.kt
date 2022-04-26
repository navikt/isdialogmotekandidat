package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.dialogmotekandidat.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.defaultZoneOffset
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OppfolgingstilfelleArbeidstakerSpek : Spek({
    describe("isDialogmotekandidat") {

        val personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER

        it("returns false if tilfelleEnd is before dialogmotekandidatStoppunktPlanlagt, and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = emptyList()
            )
            result.shouldBeFalse()
        }

        it("returns false if tilfelleEnd is equal to dialogmotekandidatStoppunktPlanlagt, and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = emptyList()
            )
            result.shouldBeTrue()
        }

        it("returns true if tilfelleEnd is after dialogmotekandidatStoppunktPlanlagt, and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeTrue()
        }

        it("returns false if latest DialogmoteKandidatEndring is Kandidat, latest dialogmotekandidatEndringStoppunkt is before tilfelleStart, and tilfelleEnd is not before dialogmotekandidatStoppunktPlanlagt") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeFalse()
        }

        it("returns false if latest dialogmotekandidatEndringStoppunkt is equal to tilfelleStart, latest DialogmoteKandidatEndring is not Kandidat, and tilfelleEnd is not before dialogmotekandidatStoppunktPlanlagt") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleStart.atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )
            val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
                dialogmotekandidatEndringFerdigstilt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeFalse()
        }

        it("returns false if latest dialogmotekandidatEndringStoppunkt is equal to tilfelleEnd, latest DialogmoteKandidatEndring is not Kandidat, and tilfelleEnd is not before dialogmotekandidatStoppunktPlanlagt") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleEnd.atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )
            val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
                dialogmotekandidatEndringFerdigstilt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeFalse()
        }

        it("returns true if latest dialogmotekandidatEndringStoppunkt is before tilfelleStart, latest DialogmoteKandidatEndring is not Kandidat, and tilfelleEnd is not before dialogmotekandidatStoppunktPlanlagt") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )
            val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
                dialogmotekandidatEndringFerdigstilt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeTrue()
        }

        it("returns true if latest dialogmotekandidatEndringStoppunkt is after tilfelleEnd, latest DialogmoteKandidatEndring is not Kandidat, and tilfelleEnd is not before dialogmotekandidatStoppunktPlanlagt") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleEnd.plusDays(1).atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )
            val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
                dialogmotekandidatEndringFerdigstilt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList
            )
            result.shouldBeTrue()
        }
    }
})
