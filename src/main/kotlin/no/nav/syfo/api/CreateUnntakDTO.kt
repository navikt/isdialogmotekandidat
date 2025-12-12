package no.nav.syfo.api

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import no.nav.syfo.domain.UnntakArsak

data class CreateUnntakDTO(
    val personident: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateUnntakDTO.toUnntak(
    createdByIdent: String,
) = Unntak(
    createdBy = createdByIdent,
    personident = Personident(this.personident),
    arsak = UnntakArsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
