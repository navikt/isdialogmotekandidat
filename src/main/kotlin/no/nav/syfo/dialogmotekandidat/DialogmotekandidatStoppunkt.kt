package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.defaultZoneOffset
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS = 112L

enum class DialogmotekandidatStoppunktStatus {
    PLANLAGT_KANDIDAT,
    KANDIDAT,
    IKKE_KANDIDAT,
}

data class DialogmotekandidatStoppunkt private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val processedAt: OffsetDateTime?,
    val status: DialogmotekandidatStoppunktStatus,
    val stoppunktPlanlagt: LocalDate,
) {
    companion object {
        fun stoppunktPlanlagtDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

        fun planlagt(
            arbeidstakerPersonIdent: PersonIdentNumber,
            tilfelleStart: LocalDate,
        ) = DialogmotekandidatStoppunkt(
            uuid = UUID.randomUUID(),
            createdAt = OffsetDateTime.now(defaultZoneOffset),
            personIdent = arbeidstakerPersonIdent,
            processedAt = null,
            status = DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT,
            stoppunktPlanlagt = stoppunktPlanlagtDato(tilfelleStart),
        )

        fun create(pDialogmotekandidatStoppunkt: PDialogmotekandidatStoppunkt) =
            DialogmotekandidatStoppunkt(
                uuid = pDialogmotekandidatStoppunkt.uuid,
                createdAt = pDialogmotekandidatStoppunkt.createdAt,
                personIdent = pDialogmotekandidatStoppunkt.personIdent,
                processedAt = pDialogmotekandidatStoppunkt.processedAt,
                status = DialogmotekandidatStoppunktStatus.valueOf(pDialogmotekandidatStoppunkt.status),
                stoppunktPlanlagt = pDialogmotekandidatStoppunkt.stoppunktPlanlagt,
            )
    }
}
