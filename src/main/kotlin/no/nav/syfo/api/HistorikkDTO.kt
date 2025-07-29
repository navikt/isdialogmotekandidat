package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Unntak
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
            unntak: List<Unntak>,
            ikkeAktuell: List<IkkeAktuell>,
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
        DialogmotekandidatEndringArsak.STOPPUNKT ->
            HistorikkDTO(
                tidspunkt = tidspunkt,
                type = HistorikkType.KANDIDAT,
                arsak = it.arsak.name,
                vurdertAv = null,
            )

        DialogmotekandidatEndringArsak.LUKKET ->
            HistorikkDTO(
                tidspunkt = tidspunkt,
                type = HistorikkType.LUKKET,
                arsak = it.arsak.name,
                vurdertAv = null,
            )

        // Disse dekkes av dialogmote-historikk og unntak/ikke-aktuell-historikk
        DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT, DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET, DialogmotekandidatEndringArsak.UNNTAK, DialogmotekandidatEndringArsak.IKKE_AKTUELL -> null
    }
}

fun List<Unntak>.toUnntakHistorikk(): List<HistorikkDTO> = this.map {
    HistorikkDTO(
        tidspunkt = it.createdAt.toLocalDateTime(),
        type = HistorikkType.UNNTAK,
        arsak = it.arsak.name,
        vurdertAv = it.createdBy,
    )
}

fun List<IkkeAktuell>.toIkkeAktuellHistorikk(): List<HistorikkDTO> = this.map {
    HistorikkDTO(
        tidspunkt = it.createdAt.toLocalDateTime(),
        type = HistorikkType.IKKE_AKTUELL,
        arsak = it.arsak.name,
        vurdertAv = it.createdBy,
    )
}

enum class HistorikkType {
    KANDIDAT,
    UNNTAK,
    IKKE_AKTUELL,
    LUKKET,
}
