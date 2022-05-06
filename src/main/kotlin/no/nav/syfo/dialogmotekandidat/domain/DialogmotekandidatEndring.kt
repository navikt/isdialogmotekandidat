package no.nav.syfo.dialogmotekandidat.domain

import no.nav.syfo.dialogmotekandidat.api.DialogmotekandidatDTO
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

enum class DialogmotekandidatEndringArsak {
    STOPPUNKT,
    DIALOGMOTE_FERDIGSTILT,
    UNNTAK,
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
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = true,
            arsak = DialogmotekandidatEndringArsak.STOPPUNKT,
        )

        fun create(pDialogmotekandidatEndring: PDialogmotekandidatEndring) =
            DialogmotekandidatEndring(
                uuid = pDialogmotekandidatEndring.uuid,
                createdAt = pDialogmotekandidatEndring.createdAt,
                personIdentNumber = pDialogmotekandidatEndring.personIdent,
                kandidat = pDialogmotekandidatEndring.kandidat,
                arsak = DialogmotekandidatEndringArsak.valueOf(pDialogmotekandidatEndring.arsak),
            )

        fun ferdigstiltDialogmote(
            personIdentNumber: PersonIdentNumber,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT
        )

        fun unntak(
            personIdentNumber: PersonIdentNumber,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = DialogmotekandidatEndringArsak.UNNTAK
        )

        private fun create(
            personIdentNumber: PersonIdentNumber,
            kandidat: Boolean,
            arsak: DialogmotekandidatEndringArsak,
        ) = DialogmotekandidatEndring(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personIdentNumber = personIdentNumber,
            kandidat = kandidat,
            arsak = arsak
        )
    }
}

fun DialogmotekandidatEndring?.toDialogmotekandidatDTO() = DialogmotekandidatDTO(
    kandidat = this?.kandidat ?: false,
)

fun DialogmotekandidatEndring.toKafkaDialogmotekandidatEndring() = KafkaDialogmotekandidatEndring(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber.value,
    kandidat = this.kandidat,
    arsak = this.arsak.name,
)

fun DialogmotekandidatEndring.isNotInOppfolgingstilfelle(
    oppfolgingstilfelle: OppfolgingstilfelleArbeidstaker,
): Boolean {
    val stoppunktKandidatAt = createdAt.toLocalDate()
    return stoppunktKandidatAt.isBefore(oppfolgingstilfelle.tilfelleStart) || stoppunktKandidatAt.isAfter(oppfolgingstilfelle.tilfelleEnd)
}

private fun DialogmotekandidatEndring?.ikkeKandidat(): Boolean = this == null || !this.kandidat

fun List<DialogmotekandidatEndring>.latest() = maxByOrNull { it.createdAt }

fun List<DialogmotekandidatEndring>.isLatestIkkeKandidat() = this.latest().ikkeKandidat()

private fun List<DialogmotekandidatEndring>.latestStoppunktKandidat() =
    filter {
        it.arsak == DialogmotekandidatEndringArsak.STOPPUNKT && it.kandidat
    }.latest()

fun List<DialogmotekandidatEndring>.isLatestStoppunktKandidatNotInOppfolgingstilfelle(
    oppfolgingstilfelle: OppfolgingstilfelleArbeidstaker,
) = latestStoppunktKandidat()
    ?.isNotInOppfolgingstilfelle(
        oppfolgingstilfelle = oppfolgingstilfelle,
    ) ?: true
