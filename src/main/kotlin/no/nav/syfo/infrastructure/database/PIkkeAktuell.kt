package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.IkkeAktuellArsak
import java.time.OffsetDateTime
import java.util.*

data class PIkkeAktuell(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<PIkkeAktuell>.toIkkeAktuellList() = this.map {
    IkkeAktuell(
        uuid = it.uuid,
        createdAt = it.createdAt,
        createdBy = it.createdBy,
        personIdent = Personident(it.personIdent),
        arsak = IkkeAktuellArsak.valueOf(it.arsak),
        beskrivelse = it.beskrivelse,
    )
}
