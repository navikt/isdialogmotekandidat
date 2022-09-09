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
import java.time.*

class OppfolgingstilfelleArbeidstakerSpek : Spek({
    describe("isDialogmotekandidat") {

        val personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER

        it("isDialogmotekandidat returns false if duration is less than DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = emptyList(),
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeFalse()
        }

        it("isDialogmotekandidat returns true if duration is equal to DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = emptyList(),
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeTrue()
        }

        it("isDialogmotekandidat returns true if duration is greater than DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeTrue()
        }

        it("isDialogmotekandidat returns false if latestDialogmoteFerdigstilt after tilfelle start") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.toOffsetDatetime().plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
            )
            result.shouldBeFalse()
        }

        it("isDialogmotekandidat returns true if latestDialogmoteFerdigstilt before tilfelle start") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.toOffsetDatetime().minusDays(1),
            )
            result.shouldBeTrue()
        }

        it("isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and within oppfolgingstilfelle") {
            val latestOppfolgingstilfelle = generateOppfolgingstilfelleArbeidstaker(
                arbeidstakerPersonIdent = personIdent,
                oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            )
            val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = personIdent,
            ).copy(
                createdAt = latestOppfolgingstilfelle.tilfelleEnd.minusDays(1).atStartOfDay()
                    .atOffset(defaultZoneOffset)
            )

            val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
                dialogmotekandidatEndringStoppunkt,
            )

            val result = latestOppfolgingstilfelle.isDialogmotekandidat(
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeFalse()
        }

        it("isDialogmotekandidat returns true if latest DialogmoteKandidatEndring is Kandidat, but is before tilfelleStart") {
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
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeTrue()
        }

        it("isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleStart") {
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
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeFalse()
        }

        it("isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleEnd") {
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
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeFalse()
        }

        it("isDialogmotekandidat returns true if latest DialogmoteKandidatEndring with kandidat==true is before tilfelleStart") {
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
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeTrue()
        }

        it("isDialogmotekandidat returns false if latest DialogmoteKandidatEndring with kandidat==true is after tilfelleStart (and after tilfelleEnd)") {
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
                dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                latestDialogmoteFerdigstilt = null,
            )
            result.shouldBeFalse()
        }
    }
})

fun LocalDate.toOffsetDatetime() = OffsetDateTime.of(this, LocalTime.NOON, ZoneOffset.UTC)
