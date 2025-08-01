package no.nav.syfo.infrastructure.clients.oppfolgingstilfelle

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.util.isBeforeOrEqual
import no.nav.syfo.util.tomorrow
import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
    val dodsdato: LocalDate? = null,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun OppfolgingstilfelleDTO.toOppfolgingstilfelle(
    personIdent: Personident,
    dodsdato: LocalDate?,
) = Oppfolgingstilfelle(
    personIdent = personIdent,
    tilfelleStart = start,
    tilfelleEnd = end,
    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
    virksomhetsnummerList = virksomhetsnummerList.map { Virksomhetsnummer(it) },
    dodsdato = dodsdato,
)

fun OppfolgingstilfellePersonDTO?.toOppfolgingstilfelleList(personIdent: Personident): List<Oppfolgingstilfelle> =
    this?.oppfolgingstilfelleList
        ?.filter { it.start.isBeforeOrEqual(tomorrow()) }
        ?.map {
            it.toOppfolgingstilfelle(
                personIdent = personIdent,
                dodsdato = this.dodsdato,
            )
        } ?: emptyList()
