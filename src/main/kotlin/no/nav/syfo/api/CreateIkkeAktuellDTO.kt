package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident

data class CreateIkkeAktuellDTO(
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateIkkeAktuellDTO.toIkkeAktuell(
    createdByIdent: String,
) = DialogmotekandidatEndring.ikkeAktuell(
    personident = Personident(this.personIdent),
    createdBy = createdByIdent,
    arsak = DialogmotekandidatEndring.IkkeAktuell.Arsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
