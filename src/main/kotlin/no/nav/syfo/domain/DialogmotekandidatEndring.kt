package no.nav.syfo.domain

import no.nav.syfo.infrastructure.database.dialogmotekandidat.PDialogmotekandidatEndring
import no.nav.syfo.util.nowUTC
import no.nav.syfo.util.toLocalDateOslo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class DialogmotekandidatEndring private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: Personident,
    val kandidat: Boolean,
    val arsak: Arsak,
) {
    fun isBeforeStartOfOppfolgingstilfelle(
        tilfelleStart: LocalDate,
    ): Boolean {
        val stoppunktKandidatAt = createdAt.toLocalDateOslo()
        return stoppunktKandidatAt.isBefore(tilfelleStart)
    }

    fun isAvventValidForDialogmotekandidatEndring(avvent: Avvent) =
        this.kandidat && avvent.createdAt.isAfter(this.createdAt) == true && !avvent.isLukket

    enum class Arsak {
        STOPPUNKT,
        DIALOGMOTE_FERDIGSTILT,
        DIALOGMOTE_LUKKET,
        UNNTAK,
        IKKE_AKTUELL,
        LUKKET,
    }

    companion object {
        fun stoppunktKandidat(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = true,
            arsak = Arsak.STOPPUNKT,
        )

        fun create(pDialogmotekandidatEndring: PDialogmotekandidatEndring) =
            DialogmotekandidatEndring(
                uuid = pDialogmotekandidatEndring.uuid,
                createdAt = pDialogmotekandidatEndring.createdAt,
                personIdentNumber = pDialogmotekandidatEndring.personident,
                kandidat = pDialogmotekandidatEndring.kandidat,
                arsak = Arsak.valueOf(pDialogmotekandidatEndring.arsak),
            )

        fun ferdigstiltDialogmote(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = Arsak.DIALOGMOTE_FERDIGSTILT
        )

        fun lukketDialogmote(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = Arsak.DIALOGMOTE_LUKKET
        )

        fun unntak(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = Arsak.UNNTAK
        )

        fun ikkeAktuell(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = Arsak.IKKE_AKTUELL,
        )

        fun lukket(
            personIdentNumber: Personident,
        ) = create(
            personIdentNumber = personIdentNumber,
            kandidat = false,
            arsak = Arsak.LUKKET
        )

        private fun create(
            personIdentNumber: Personident,
            kandidat: Boolean,
            arsak: Arsak,
        ) = DialogmotekandidatEndring(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personIdentNumber = personIdentNumber,
            kandidat = kandidat,
            arsak = arsak
        )
    }
}

private fun DialogmotekandidatEndring?.ikkeKandidat(): Boolean = this == null || !this.kandidat

fun List<DialogmotekandidatEndring>.latest() = maxByOrNull { it.createdAt }

fun List<DialogmotekandidatEndring>.isLatestIkkeKandidat() = this.latest().ikkeKandidat()

private fun List<DialogmotekandidatEndring>.latestStoppunktKandidat() =
    filter {
        it.arsak == DialogmotekandidatEndring.Arsak.STOPPUNKT && it.kandidat
    }.latest()

fun List<DialogmotekandidatEndring>.isLatestStoppunktKandidatMissingOrNotInOppfolgingstilfelle(
    tilfelleStart: LocalDate,
) = latestStoppunktKandidat()
    ?.isBeforeStartOfOppfolgingstilfelle(
        tilfelleStart = tilfelleStart,
    ) ?: true
