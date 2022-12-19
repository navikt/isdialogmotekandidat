package no.nav.syfo.unntak.domain

import java.time.LocalDate

data class UnntakStatistikk(
    val unntakDato: LocalDate,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
)
