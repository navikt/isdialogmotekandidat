package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

data class PDialogmotekandidatEndring(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val kandidat: Boolean,
    val arsak: String,
)

fun List<PDialogmotekandidatEndring>.toDialogmotekandidatEndringList() = map { it.toDialogmotekandidatEndring() }

fun PDialogmotekandidatEndring.toDialogmotekandidatEndring() = DialogmotekandidatEndring.create(this)
