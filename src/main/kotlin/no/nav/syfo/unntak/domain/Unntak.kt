package no.nav.syfo.unntak.domain

import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

enum class UnntakArsak {
    MEDISINSKE_GRUNNER,
    INNLEGGELSE_INSTITUSJON,
    FORVENTET_FRISKMELDING_INNEN_28UKER,
    DOKUMENTERT_TILTAK_FRISKMELDING
}

data class Unntak(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: PersonIdentNumber,
    val arsak: UnntakArsak,
)
