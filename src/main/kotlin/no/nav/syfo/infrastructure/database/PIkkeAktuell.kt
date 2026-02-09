package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import java.time.OffsetDateTime
import java.util.*

data class PIkkeAktuell(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personident: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<PIkkeAktuell>.toIkkeAktuellList() = this.map {
    IkkeAktuell(
        uuid = it.uuid,
        createdAt = it.createdAt,
        createdBy = it.createdBy,
        personident = Personident(it.personident),
        arsak = IkkeAktuell.Arsak.valueOf(it.arsak),
        beskrivelse = it.beskrivelse,
    )
}
