package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident

data class CreateUnntakDTO(
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateUnntakDTO.toUnntak(
    createdByIdent: String,
) = DialogmotekandidatEndring.unntak(
    personident = Personident(this.personIdent),
    createdBy = createdByIdent,
    arsak = DialogmotekandidatEndring.Unntak.Arsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
