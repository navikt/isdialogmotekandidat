package no.nav.syfo.client.oppfolgingstilfelle

import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun OppfolgingstilfellePersonDTO.findOppfolgingstilfelleByDate(date: LocalDate): OppfolgingstilfelleDTO? {
    val oppfolgingstilfeller = this.oppfolgingstilfelleList.filter {
        it.start.isBeforeOrEqual(date) && it.end.isAfterOrEqual(date)
    }

    return oppfolgingstilfeller.minByOrNull { it.start }
}
