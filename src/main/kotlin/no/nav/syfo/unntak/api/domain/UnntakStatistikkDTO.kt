package no.nav.syfo.unntak.api.domain

import java.time.LocalDate

data class UnntakStatistikkDTO(
    val unntakDato: LocalDate,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
)
