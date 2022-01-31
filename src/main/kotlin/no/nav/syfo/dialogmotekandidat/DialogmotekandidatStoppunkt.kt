package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

enum class DialogmotekandidatStoppunktStatus {
    PLANLAGT_KANDIDAT,
    KANDIDAT,
    IKKE_KANDIDAT,
}

data class DialogmotekandidatStoppunkt(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val processedAt: OffsetDateTime?,
    val status: DialogmotekandidatStoppunktStatus,
    val stoppunktPlanlagt: LocalDate,
)
