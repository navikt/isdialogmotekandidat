package no.nav.syfo.dialogmotekandidat.midlertidig

import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

data class PMidlertidigDialogmotekandidatEndring(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val kandidat: Boolean,
    val arsak: String,
)

fun List<PMidlertidigDialogmotekandidatEndring>.toDialogmotekandidatEndringList() = map {
    it.toDialogmotekandidatEndring()
}

fun PMidlertidigDialogmotekandidatEndring.toDialogmotekandidatEndring() = DialogmotekandidatEndring.create(this)
