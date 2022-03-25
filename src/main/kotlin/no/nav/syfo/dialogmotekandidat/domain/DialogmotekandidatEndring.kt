package no.nav.syfo.dialogmotekandidat.domain

import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

enum class DialogmotekandidatEndringArsak {
    STOPPUNKT
}

data class DialogmotekandidatEndring private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val kandidat: Boolean,
    val arsak: DialogmotekandidatEndringArsak,
) {
    companion object {
        fun stoppunktKandidat(
            personIdentNumber: PersonIdentNumber,
        ) = DialogmotekandidatEndring(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personIdentNumber = personIdentNumber,
            kandidat = true,
            arsak = DialogmotekandidatEndringArsak.STOPPUNKT,
        )
    }
}

fun DialogmotekandidatEndring.toKafkaDialogmotekandidatEndring() = KafkaDialogmotekandidatEndring(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber.value,
    kandidat = this.kandidat,
    arsak = this.arsak.name,
)
