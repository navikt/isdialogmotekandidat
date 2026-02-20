package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import java.time.LocalDateTime

data class HistorikkDTO(
    val tidspunkt: LocalDateTime,
    val type: HistorikkType,
    val arsak: String,
    val vurdertAv: String?,
) {
    companion object {
        fun fromDialogmotekandidatEndringer(
            dialogmotekandidatEndringer: List<DialogmotekandidatEndring>,
        ): List<HistorikkDTO> {
            val historikk = dialogmotekandidatEndringer.mapNotNull { from(it) }
            return historikk.sortedByDescending { it.tidspunkt }
        }

        private fun from(dialogmotekandidatEndring: DialogmotekandidatEndring): HistorikkDTO? =
            when (dialogmotekandidatEndring) {
                is DialogmotekandidatEndring.Kandidat ->
                    HistorikkDTO(
                        tidspunkt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                        type = HistorikkType.KANDIDAT,
                        arsak = dialogmotekandidatEndring.arsak.name,
                        vurdertAv = null,
                    )
                is DialogmotekandidatEndring.Unntak ->
                    HistorikkDTO(
                        tidspunkt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                        type = HistorikkType.UNNTAK,
                        arsak = dialogmotekandidatEndring.unntakArsak.name,
                        vurdertAv = dialogmotekandidatEndring.createdBy,
                    )
                is DialogmotekandidatEndring.IkkeAktuell ->
                    HistorikkDTO(
                        tidspunkt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                        type = HistorikkType.IKKE_AKTUELL,
                        arsak = dialogmotekandidatEndring.ikkeAktuellArsak.name,
                        vurdertAv = dialogmotekandidatEndring.createdBy,
                    )
                is DialogmotekandidatEndring.Lukket ->
                    HistorikkDTO(
                        tidspunkt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                        type = HistorikkType.LUKKET,
                        arsak = dialogmotekandidatEndring.arsak.name,
                        vurdertAv = null,
                    )

                // Disse dekkes av dialogmote-historikk
                else -> null
            }
    }
}

enum class HistorikkType {
    KANDIDAT,
    UNNTAK,
    IKKE_AKTUELL,
    LUKKET,
}
