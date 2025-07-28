package no.nav.syfo.api

import java.time.LocalDateTime

data class DialogmotekandidatDTO(
    val kandidat: Boolean,
    val kandidatAt: LocalDateTime?,
)
