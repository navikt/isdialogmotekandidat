package no.nav.syfo.unntak.database.domain

import java.time.OffsetDateTime
import java.util.*

data class PUnntak(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: String,
    val arsak: String,
)
