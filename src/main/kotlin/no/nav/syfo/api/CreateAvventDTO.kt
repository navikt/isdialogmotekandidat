package no.nav.syfo.api

import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.Personident
import java.time.LocalDate

data class CreateAvventDTO(
    val personIdent: String,
    val frist: LocalDate,
    val beskrivelse: String?,
)

fun CreateAvventDTO.toAvvent(
    createdByIdent: String,
) = Avvent(
    frist = this.frist,
    createdBy = createdByIdent,
    personIdent = Personident(this.personIdent),
    beskrivelse = this.beskrivelse,
)
