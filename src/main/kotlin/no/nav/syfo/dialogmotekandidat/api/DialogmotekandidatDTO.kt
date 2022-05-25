package no.nav.syfo.dialogmotekandidat.api

import java.time.LocalDateTime

data class DialogmotekandidatDTO(
    val kandidat: Boolean,
    val kandidatAt: LocalDateTime?,
)
