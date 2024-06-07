package no.nav.syfo.ikkeaktuell.domain

import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

enum class IkkeAktuellArsak {
    ARBEIDSTAKER_AAP,
    ARBEIDSTAKER_DOD,
    DIALOGMOTE_AVHOLDT,
}

data class IkkeAktuell(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: PersonIdentNumber,
    val arsak: IkkeAktuellArsak,
    val beskrivelse: String?,
)
