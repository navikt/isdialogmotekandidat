package no.nav.syfo.api

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Unntak
import no.nav.syfo.domain.UnntakArsak

data class CreateUnntakDTO(
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateUnntakDTO.toUnntak(
    createdByIdent: String,
) = Unntak(
    createdBy = createdByIdent,
    personIdent = PersonIdentNumber(this.personIdent),
    arsak = UnntakArsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
