package no.nav.syfo.domain

import no.nav.syfo.infrastructure.database.dialogmotekandidat.PDialogmotekandidatStoppunkt
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS = 119L

// Oppfølgingstilfeller som begynner før denne datoen antas å ha blitt generert som kandidater i Arena,
// så vi genererer ikke stoppunkt fram i tid for disse
var ARENA_CUTOFF = LocalDate.of(2022, 5, 21)

enum class DialogmotekandidatStoppunktStatus {
    PLANLAGT_KANDIDAT,
    KANDIDAT,
    IKKE_KANDIDAT,
}

data class DialogmotekandidatStoppunkt private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: Personident,
    val processedAt: OffsetDateTime?,
    val status: DialogmotekandidatStoppunktStatus,
    val stoppunktPlanlagt: LocalDate,
) {
    companion object {
        fun stoppunktPlanlagtDato(
            tilfelleStart: LocalDate,
            tilfelleEnd: LocalDate,
        ): LocalDate {
            val today = LocalDate.now()
            val stoppunkt = tilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
            return if (tilfelleStart.isBefore(ARENA_CUTOFF)) stoppunkt else {
                if (stoppunkt.isBefore(today) && today.isBefore(tilfelleEnd)) today else stoppunkt
            }
        }

        fun planlagt(
            arbeidstakerPersonIdent: Personident,
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
