package no.nav.syfo.unntak.api.domain

import java.time.LocalDateTime

data class UnntakDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val personIdent: String,
    val arsak: String,
)
