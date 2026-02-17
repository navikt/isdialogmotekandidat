package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import java.time.OffsetDateTime
import java.util.*

data class PUnntak(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personident: String,
    val arsak: String,
    val beskrivelse: String?,
)

fun List<PUnntak>.toUnntakList() = this.map { pUnntak ->
    DialogmotekandidatEndring.Unntak(
        uuid = pUnntak.uuid,
        createdAt = pUnntak.createdAt,
        personident = Personident(pUnntak.personident),
        createdBy = pUnntak.createdBy,
        unntakArsak = DialogmotekandidatEndring.Unntak.Arsak.valueOf(pUnntak.arsak),
        beskrivelse = pUnntak.beskrivelse,
    )
}
