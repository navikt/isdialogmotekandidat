package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import java.time.LocalDate

data class CreateAvventDTO(
    val personident: String,
    val frist: LocalDate,
    val beskrivelse: String,
) {
    fun toAvvent(
        createdByIdent: String,
    ) = DialogmotekandidatEndring.avvent(
        frist = this.frist,
        createdBy = createdByIdent,
        personident = Personident(this.personident),
        beskrivelse = this.beskrivelse,
    )
}
