package no.nav.syfo.unntak.database.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.unntak.domain.UnntakArsak
import java.time.OffsetDateTime
import java.util.*

data class PUnntak(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<PUnntak>.toUnntakList() = this.map { pUnntak ->
    Unntak.createFromDatabase(
        uuid = pUnntak.uuid,
        createdAt = pUnntak.createdAt,
        createdBy = pUnntak.createdBy,
        personIdent = PersonIdentNumber(pUnntak.personIdent),
        arsak = UnntakArsak.valueOf(pUnntak.arsak),
        beskrivelse = pUnntak.beskrivelse,
    )
}
