package no.nav.syfo.ikkeaktuell.api.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.ikkeaktuell.domain.IkkeAktuell
import no.nav.syfo.ikkeaktuell.domain.IkkeAktuellArsak
import no.nav.syfo.util.nowUTC
import java.util.*

data class CreateIkkeAktuellDTO(
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun CreateIkkeAktuellDTO.toIkkeAktuell(
    createdByIdent: String,
) = IkkeAktuell(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    createdBy = createdByIdent,
    personIdent = PersonIdentNumber(this.personIdent),
    arsak = IkkeAktuellArsak.valueOf(this.arsak),
    beskrivelse = this.beskrivelse,
)
