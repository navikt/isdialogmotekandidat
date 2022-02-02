package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class OppfolgingstilfelleArbeidstaker(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val tilfelleGenerert: OffsetDateTime,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

fun OppfolgingstilfelleArbeidstaker.isDialogmotekandidat(): Boolean {
    val dialogmotekandidatStoppunktPlanlagt = DialogmotekandidatStoppunkt.stoppunktPlanlagtDato(tilfelleStart)
    return tilfelleEnd.isEqual(dialogmotekandidatStoppunktPlanlagt) || tilfelleEnd.isAfter(dialogmotekandidatStoppunktPlanlagt)
}

fun OppfolgingstilfelleArbeidstaker.toDialogmotekandidatStoppunktPlanlagt() =
    DialogmotekandidatStoppunkt.planlagt(
        arbeidstakerPersonIdent = this.personIdent,
        tilfelleStart = this.tilfelleStart,
    )
