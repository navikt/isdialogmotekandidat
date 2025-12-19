package no.nav.syfo.api

import java.time.LocalDateTime
import java.util.UUID

data class GetDialogmotekandidaterRequestDTO(
    val personidenter: List<String>
)

data class GetDialogmotekandidatForPersonsResponseDTO(
    val dialogmotekandidater: Map<String, DialogmotekandidatResponseDTO>,
)

data class DialogmotekandidatResponseDTO(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val personident: String,
    val isKandidat: Boolean,
    val avvent: AvventDTO?,
)
