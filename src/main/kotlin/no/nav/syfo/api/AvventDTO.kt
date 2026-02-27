package no.nav.syfo.api

import no.nav.syfo.domain.DialogmotekandidatEndring
import java.time.LocalDate
import java.time.LocalDateTime

data class AvventDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personident: String,
    val beskrivelse: String,
)

fun List<DialogmotekandidatEndring.Avvent>.toAvventDTOList() =
    this.map { avvent ->
        AvventDTO(
            uuid = avvent.uuid.toString(),
            createdAt = avvent.createdAt.toLocalDateTime(),
            frist = avvent.frist,
            createdBy = avvent.createdBy,
            personident = avvent.personident.value,
            beskrivelse = avvent.beskrivelse,
        )
    }
