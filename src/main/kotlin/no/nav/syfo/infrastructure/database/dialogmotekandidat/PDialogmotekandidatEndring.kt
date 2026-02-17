package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import java.time.OffsetDateTime
import java.util.*

data class PDialogmotekandidatEndring(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: Personident,
    val kandidat: Boolean,
    val arsak: String,
)

fun List<PDialogmotekandidatEndring>.toDialogmotekandidatEndringList() = map { it.toDialogmotekandidatEndring() }

fun PDialogmotekandidatEndring.toDialogmotekandidatEndring(): DialogmotekandidatEndring =
    DialogmotekandidatEndring.Endring(
        uuid = uuid,
        createdAt = createdAt,
        personident = personident,
        kandidat = kandidat,
        arsak = DialogmotekandidatEndring.Arsak.valueOf(arsak),
    )
