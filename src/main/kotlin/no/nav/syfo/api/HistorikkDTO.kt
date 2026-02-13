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
        fun createHistorikkDTOs(
            dialogmotekandidatEndringer: List<DialogmotekandidatEndring>,
            unntak: List<DialogmotekandidatEndring.Unntak>,
            ikkeAktuell: List<DialogmotekandidatEndring.IkkeAktuell>,
        ): List<HistorikkDTO> {
            val historikk =
                dialogmotekandidatEndringer.toKandidatHistorikk() + unntak.toUnntakHistorikk() + ikkeAktuell.toIkkeAktuellHistorikk()
            return historikk.sortedByDescending { it.tidspunkt }
        }
    }
}

fun List<DialogmotekandidatEndring>.toKandidatHistorikk(): List<HistorikkDTO> = this.mapNotNull {
    val tidspunkt = it.createdAt.toLocalDateTime()
    when (it.arsak) {
        DialogmotekandidatEndring.Arsak.STOPPUNKT ->
            HistorikkDTO(
                tidspunkt = tidspunkt,
                type = HistorikkType.KANDIDAT,
                arsak = it.arsak.name,
                vurdertAv = null,
            )

        DialogmotekandidatEndring.Arsak.LUKKET ->
            HistorikkDTO(
                tidspunkt = tidspunkt,
                type = HistorikkType.LUKKET,
                arsak = it.arsak.name,
                vurdertAv = null,
            )

        // Disse dekkes av dialogmote-historikk og unntak/ikke-aktuell-historikk
        DialogmotekandidatEndring.Arsak.DIALOGMOTE_FERDIGSTILT, DialogmotekandidatEndring.Arsak.DIALOGMOTE_LUKKET, DialogmotekandidatEndring.Arsak.UNNTAK, DialogmotekandidatEndring.Arsak.IKKE_AKTUELL -> null
    }
}

fun List<DialogmotekandidatEndring.Unntak>.toUnntakHistorikk(): List<HistorikkDTO> = this.map {
    HistorikkDTO(
        tidspunkt = it.createdAt.toLocalDateTime(),
        type = HistorikkType.UNNTAK,
        arsak = it.unntakArsak.name,
        vurdertAv = it.createdBy,
    )
}

fun List<DialogmotekandidatEndring.IkkeAktuell>.toIkkeAktuellHistorikk(): List<HistorikkDTO> = this.map {
    HistorikkDTO(
        tidspunkt = it.createdAt.toLocalDateTime(),
        type = HistorikkType.IKKE_AKTUELL,
        arsak = it.ikkeAktuellArsak.name,
        vurdertAv = it.createdBy,
    )
}

enum class HistorikkType {
    KANDIDAT,
    UNNTAK,
    IKKE_AKTUELL,
    LUKKET,
}
