package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

enum class UnntakArsak {
    MEDISINSKE_GRUNNER,
    INNLEGGELSE_INSTITUSJON,

    @Deprecated("Brukes ikke lenger for Unntak, bruk IkkeAktuell")
    FRISKMELDT,
    FORVENTET_FRISKMELDING_INNEN_28UKER,
    DOKUMENTERT_TILTAK_FRISKMELDING,

    @Deprecated("Brukes ikke lenger for Unntak, bruk IkkeAktuell")
    ARBEIDSFORHOLD_OPPHORT,
}

data class Unntak private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personident: Personident,
    val arsak: UnntakArsak,
    val beskrivelse: String?,
) {

    constructor(
        createdBy: String,
        personident: Personident,
        arsak: UnntakArsak,
        beskrivelse: String?,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        createdBy = createdBy,
        personident = personident,
        arsak = arsak,
        beskrivelse = beskrivelse,
    ) {
        if (arsak in listOf(UnntakArsak.FRISKMELDT, UnntakArsak.ARBEIDSFORHOLD_OPPHORT)) {
            throw IllegalArgumentException("$arsak skal ikke brukes for nye unntak, bruk IkkeAktuell")
        }
    }

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            createdBy: String,
            personident: Personident,
            arsak: UnntakArsak,
            beskrivelse: String?,
        ) = Unntak(
            uuid = uuid,
            createdAt = createdAt,
            createdBy = createdBy,
            personident = personident,
            arsak = arsak,
            beskrivelse = beskrivelse,
        )
    }
}
