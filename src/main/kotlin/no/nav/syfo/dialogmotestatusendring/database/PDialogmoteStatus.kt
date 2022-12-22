package no.nav.syfo.dialogmotestatusendring.database

import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

data class PDialogmoteStatus(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val moteTidspunkt: OffsetDateTime,
    val ferdigstiltTidspunkt: OffsetDateTime,
)
