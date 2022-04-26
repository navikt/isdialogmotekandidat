package no.nav.syfo.unntak.api.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.NewUnntak

data class NewUnntakDTO(
    val personIdent: String,
)

fun NewUnntakDTO.toNewUnntak() = NewUnntak(
    personIdent = PersonIdentNumber(this.personIdent),
)
