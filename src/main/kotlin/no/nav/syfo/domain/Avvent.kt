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
    val personident: Personident,
    val beskrivelse: String,
    val isLukket: Boolean,
) {

    constructor(
        frist: LocalDate,
        createdBy: String,
        personident: Personident,
        beskrivelse: String,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        frist = frist,
        createdBy = createdBy,
        personident = personident,
        beskrivelse = beskrivelse,
        isLukket = false,
    )

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            frist: LocalDate,
            createdBy: String,
            personident: Personident,
            beskrivelse: String,
            lukket: Boolean,
        ) = Avvent(
            uuid = uuid,
            createdAt = createdAt,
            frist = frist,
            createdBy = createdBy,
            personident = personident,
            beskrivelse = beskrivelse,
            isLukket = lukket,
        )
    }
}
