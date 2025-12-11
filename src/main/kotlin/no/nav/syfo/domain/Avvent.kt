package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class Avvent private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personIdent: Personident,
    val beskrivelse: String?,
) {

    constructor(
        frist: LocalDate,
        createdBy: String,
        personIdent: Personident,
        beskrivelse: String?,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        frist = frist,
        createdBy = createdBy,
        personIdent = personIdent,
        beskrivelse = beskrivelse,
    )

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            frist: LocalDate,
            createdBy: String,
            personIdent: Personident,
            beskrivelse: String?,
        ) = Avvent(
            uuid = uuid,
            createdAt = createdAt,
            frist = frist,
            createdBy = createdBy,
            personIdent = personIdent,
            beskrivelse = beskrivelse,
        )
    }
}
