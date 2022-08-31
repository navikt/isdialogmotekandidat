package no.nav.syfo.dialogmotekandidat.domain

import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS = 119L

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
        fun stoppunktPlanlagtDato(
            tilfelleStart: LocalDate,
            tilfelleEnd: LocalDate,
        ): LocalDate {
            val stoppunkt = tilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
            val today = LocalDate.now()
            return if (stoppunkt.isBefore(today) && today.isBefore(tilfelleEnd)) today.plusDays(1) else stoppunkt
        }

        fun planlagt(
            arbeidstakerPersonIdent: PersonIdentNumber,
            tilfelleStart: LocalDate,
            tilfelleEnd: LocalDate,
        ) = DialogmotekandidatStoppunkt(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personIdent = arbeidstakerPersonIdent,
            processedAt = null,
            status = DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT,
            stoppunktPlanlagt = stoppunktPlanlagtDato(tilfelleStart, tilfelleEnd),
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

fun DialogmotekandidatStoppunkt.toDialogmotekandidatEndring() = DialogmotekandidatEndring.stoppunktKandidat(
    personIdentNumber = this.personIdent
)
