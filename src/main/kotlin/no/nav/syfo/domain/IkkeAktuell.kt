package no.nav.syfo.domain

import java.time.OffsetDateTime
import java.util.*

enum class IkkeAktuellArsak {
    ARBEIDSTAKER_AAP,
    ARBEIDSTAKER_DOD,
    DIALOGMOTE_AVHOLDT,
    FRISKMELDT,
    ARBEIDSFORHOLD_OPPHORT,
}

data class IkkeAktuell(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: Personident,
    val arsak: IkkeAktuellArsak,
    val beskrivelse: String?,
)
