package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import java.time.LocalDateTime

data class UnntakDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<DialogmotekandidatEndring.Unntak>.toUnntakDTOList() = this.map { unntak ->
    UnntakDTO(
        uuid = unntak.uuid.toString(),
        createdAt = unntak.createdAt.toLocalDateTime(),
        createdBy = unntak.createdBy,
        personIdent = unntak.personident.value,
        arsak = unntak.unntakArsak.name,
        beskrivelse = unntak.beskrivelse,
    )
}
