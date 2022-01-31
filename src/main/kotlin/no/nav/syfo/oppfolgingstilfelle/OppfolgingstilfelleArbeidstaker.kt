package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunkt
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS = 112L

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
    val dialogmotekandidatStoppunktPlanlagt = this.dialogmotekandidatStoppunktPlanlagt()
    return tilfelleEnd.isEqual(dialogmotekandidatStoppunktPlanlagt) || tilfelleEnd.isAfter(dialogmotekandidatStoppunktPlanlagt)
}

private fun OppfolgingstilfelleArbeidstaker.dialogmotekandidatStoppunktPlanlagt(): LocalDate =
    this.tilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

fun OppfolgingstilfelleArbeidstaker.toDialogmotekandidatStoppunktPlanlagt() =
    DialogmotekandidatStoppunkt(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        personIdent = this.personIdent,
        processedAt = null,
        status = DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT,
        stoppunktPlanlagt = this.dialogmotekandidatStoppunktPlanlagt()
    )
