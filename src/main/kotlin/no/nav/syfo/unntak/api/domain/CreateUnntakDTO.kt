package no.nav.syfo.unntak.api.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.unntak.domain.UnntakArsak

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
