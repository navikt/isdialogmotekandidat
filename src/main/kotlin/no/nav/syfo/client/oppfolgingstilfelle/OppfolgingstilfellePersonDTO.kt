package no.nav.syfo.client.oppfolgingstilfelle

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
