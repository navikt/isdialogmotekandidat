package no.nav.syfo.domain

import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.LocalDate
import java.time.LocalDateTime

data class Dialogmotekandidat private constructor(
    val kandidat: Boolean,
    val kandidatAt: LocalDateTime?,
) {
    companion object {

        fun create(
            latestDialogmotekandidatEndring: DialogmotekandidatEndring?,
            latestOppfolgingstilfelleStart: LocalDate?,
        ): Dialogmotekandidat {
            val kandidatAtDate = latestDialogmotekandidatEndring?.createdAt?.toLocalDate()
            val isKandidatAfterOppfolgingstilfelleStart =
                latestOppfolgingstilfelleStart != null && kandidatAtDate != null && kandidatAtDate.isAfterOrEqual(
                    latestOppfolgingstilfelleStart
                )
            val isSevenDaysPassedSinceKandidat = kandidatAtDate != null && kandidatAtDate.isBeforeOrEqual(LocalDate.now().minusDays(7))

            return if (isKandidatAfterOppfolgingstilfelleStart && isSevenDaysPassedSinceKandidat) {
                Dialogmotekandidat(
                    kandidat = latestDialogmotekandidatEndring.kandidat,
                    kandidatAt = if (latestDialogmotekandidatEndring.kandidat) latestDialogmotekandidatEndring.createdAt.toLocalDateTimeOslo() else null,
                )
            } else {
                Dialogmotekandidat(
                    kandidat = false,
                    kandidatAt = null,
                )
            }
        }
    }
}
