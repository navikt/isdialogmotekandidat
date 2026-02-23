package no.nav.syfo.domain

import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate
import java.time.OffsetDateTime

data class Oppfolgingstilfelle(
    val personident: Personident,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val arbeidstakerAtTilfelleEnd: Boolean,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
    val dodsdato: LocalDate?,
) {
    fun isDialogmotekandidat(
        dialogmotekandidatEndringList: List<DialogmotekandidatEndring>,
        latestDialogmoteFerdigstilt: OffsetDateTime?,
    ) = isDialogmotekandidat() &&
        (latestDialogmoteFerdigstilt == null || latestDialogmoteFerdigstilt.toLocalDate().isBefore(tilfelleStart)) &&
        dialogmotekandidatEndringList.isLatestStoppunktKandidatMissingOrNotInOppfolgingstilfelle(
            tilfelleStart = tilfelleStart,
        )

    fun isDialogmotekandidat(): Boolean {
        val dialogmotekandidatStoppunktPlanlagt = DialogmotekandidatStoppunkt.stoppunktPlanlagtDato(tilfelleStart, tilfelleEnd)
        return dodsdato == null && arbeidstakerAtTilfelleEnd && tilfelleEnd.isAfterOrEqual(dialogmotekandidatStoppunktPlanlagt)
    }

    fun toDialogmotekandidatStoppunktPlanlagt() =
        DialogmotekandidatStoppunkt.planlagt(
            arbeidstakerPersonIdent = personident,
            tilfelleStart = tilfelleStart,
            tilfelleEnd = tilfelleEnd,
        )
}

fun List<Oppfolgingstilfelle>.tilfelleForDate(date: LocalDate) =
    this.firstOrNull { it.tilfelleStart.isBeforeOrEqual(date) && it.tilfelleEnd.isAfterOrEqual(date) }
