package no.nav.syfo.dialogmotekandidat.database

import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PDialogmotekandidatStoppunkt(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val processedAt: OffsetDateTime?,
    val status: String,
    val stoppunktPlanlagt: LocalDate,
)

fun List<PDialogmotekandidatStoppunkt>.toDialogmotekandidatStoppunktList() = this.map {
    DialogmotekandidatStoppunkt.create(it)
}
