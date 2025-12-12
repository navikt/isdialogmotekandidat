package no.nav.syfo.api

import no.nav.syfo.domain.Unntak
import java.time.LocalDateTime

data class UnntakDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val personident: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<Unntak>.toUnntakDTOList() = this.map { unntak ->
    UnntakDTO(
        uuid = unntak.uuid.toString(),
        createdAt = unntak.createdAt.toLocalDateTime(),
        createdBy = unntak.createdBy,
        personident = unntak.personident.value,
        arsak = unntak.arsak.name,
        beskrivelse = unntak.beskrivelse,
    )
}
