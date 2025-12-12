package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.Personident
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PDialogmotekandidatStoppunkt(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: Personident,
    val processedAt: OffsetDateTime?,
    val status: String,
    val stoppunktPlanlagt: LocalDate,
)

fun List<PDialogmotekandidatStoppunkt>.toDialogmotekandidatStoppunktList() = this.map {
    DialogmotekandidatStoppunkt.create(it)
}
