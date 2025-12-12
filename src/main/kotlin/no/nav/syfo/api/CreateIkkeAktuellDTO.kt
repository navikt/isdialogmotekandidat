package no.nav.syfo.api

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.IkkeAktuellArsak
import no.nav.syfo.util.nowUTC
import java.util.*

data class CreateIkkeAktuellDTO(
    val personident: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateIkkeAktuellDTO.toIkkeAktuell(
    createdByIdent: String,
) = IkkeAktuell(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    createdBy = createdByIdent,
    personident = Personident(this.personident),
    arsak = IkkeAktuellArsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
