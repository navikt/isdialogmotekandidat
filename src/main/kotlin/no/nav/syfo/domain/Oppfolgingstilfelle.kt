package no.nav.syfo.domain

import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate
import java.time.OffsetDateTime

data class Oppfolgingstilfelle(
    val personIdent: Personident,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val arbeidstakerAtTilfelleEnd: Boolean,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
    val dodsdato: LocalDate?,
)

fun Oppfolgingstilfelle.isDialogmotekandidat(
    dialogmotekandidatEndringList: List<DialogmotekandidatEndring>,
    latestDialogmoteFerdigstilt: OffsetDateTime?,
) = this.isDialogmotekandidat() &&
    (latestDialogmoteFerdigstilt == null || latestDialogmoteFerdigstilt.toLocalDate().isBefore(this.tilfelleStart)) &&
    dialogmotekandidatEndringList.isLatestStoppunktKandidatMissingOrNotInOppfolgingstilfelle(
        tilfelleStart = this.tilfelleStart,
    )

fun Oppfolgingstilfelle.isDialogmotekandidat(): Boolean {
    val dialogmotekandidatStoppunktPlanlagt = DialogmotekandidatStoppunkt.stoppunktPlanlagtDato(tilfelleStart, tilfelleEnd)
    return dodsdato == null && arbeidstakerAtTilfelleEnd && tilfelleEnd.isAfterOrEqual(dialogmotekandidatStoppunktPlanlagt)
}

fun Oppfolgingstilfelle.toDialogmotekandidatStoppunktPlanlagt() =
    DialogmotekandidatStoppunkt.planlagt(
        arbeidstakerPersonIdent = this.personIdent,
        tilfelleStart = this.tilfelleStart,
        tilfelleEnd = this.tilfelleEnd,
    )

fun List<Oppfolgingstilfelle>.tilfelleForDate(date: LocalDate) =
    this.firstOrNull { it.tilfelleStart.isBeforeOrEqual(date) && it.tilfelleEnd.isAfterOrEqual(date) }
