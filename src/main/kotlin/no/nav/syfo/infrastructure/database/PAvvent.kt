package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.Personident
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAvvent(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personIdent: String,
    val beskrivelse: String?,
)

fun List<PAvvent>.toAvventList() = this.map { pAvvent ->
    Avvent.createFromDatabase(
        uuid = pAvvent.uuid,
        createdAt = pAvvent.createdAt,
        frist = pAvvent.frist,
        createdBy = pAvvent.createdBy,
        personIdent = Personident(pAvvent.personIdent),
        beskrivelse = pAvvent.beskrivelse,
    )
}
