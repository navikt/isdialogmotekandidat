package no.nav.syfo.api

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak

data class CreateUnntakDTO(
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateUnntakDTO.toUnntak(
    createdByIdent: String,
) = Unntak(
    createdBy = createdByIdent,
    personident = Personident(this.personIdent),
    arsak = Unntak.Arsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
