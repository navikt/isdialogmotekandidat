package no.nav.syfo.unntak.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

enum class UnntakArsak {
    MEDISINSKE_GRUNNER,
    INNLEGGELSE_INSTITUSJON,
    FRISKMELDT,
    FORVENTET_FRISKMELDING_INNEN_28UKER,
    DOKUMENTERT_TILTAK_FRISKMELDING,
    ARBEIDSFORHOLD_OPPHORT,
}

data class Unntak private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: PersonIdentNumber,
    val arsak: UnntakArsak,
    val beskrivelse: String?,
) {

    constructor(
        createdBy: String,
        personIdent: PersonIdentNumber,
        arsak: UnntakArsak,
        beskrivelse: String?,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        createdBy = createdBy,
        personIdent = personIdent,
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
            personIdent: PersonIdentNumber,
            arsak: UnntakArsak,
            beskrivelse: String?,
        ) = Unntak(
            uuid = uuid,
            createdAt = createdAt,
            createdBy = createdBy,
            personIdent = personIdent,
            arsak = arsak,
            beskrivelse = beskrivelse,
        )
    }
}
