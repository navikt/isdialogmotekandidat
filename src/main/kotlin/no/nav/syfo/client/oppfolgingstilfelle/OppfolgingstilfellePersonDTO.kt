package no.nav.syfo.client.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
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
    personIdent: PersonIdentNumber,
    dodsdato: LocalDate?,
) = Oppfolgingstilfelle(
    personIdent = personIdent,
    tilfelleStart = start,
    tilfelleEnd = end,
    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
    virksomhetsnummerList = virksomhetsnummerList.map { Virksomhetsnummer(it) },
    dodsdato = dodsdato,
)

fun OppfolgingstilfellePersonDTO?.toOppfolgingstilfelleList(personIdent: PersonIdentNumber): List<Oppfolgingstilfelle> =
    this?.oppfolgingstilfelleList
        ?.filter { it.start.isBeforeOrEqual(tomorrow()) }
        ?.map {
            it.toOppfolgingstilfelle(
                personIdent = personIdent,
                dodsdato = this.dodsdato,
            )
        } ?: emptyList()
