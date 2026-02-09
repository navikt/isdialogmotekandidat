package no.nav.syfo.domain

import java.time.OffsetDateTime
import java.util.*

data class IkkeAktuell(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personident: Personident,
    val arsak: Arsak,
    val beskrivelse: String?,
) {
    enum class Arsak {
        ARBEIDSTAKER_AAP,
        ARBEIDSTAKER_DOD,
        DIALOGMOTE_AVHOLDT,
        FRISKMELDT,
        ARBEIDSFORHOLD_OPPHORT,
    }
}
