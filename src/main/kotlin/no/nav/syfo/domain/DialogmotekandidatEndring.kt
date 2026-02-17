package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import no.nav.syfo.util.toLocalDateOslo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed class DialogmotekandidatEndring {
    abstract val uuid: UUID
    abstract val createdAt: OffsetDateTime
    abstract val personident: Personident
    abstract val kandidat: Boolean
    abstract val arsak: Arsak

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

    data class Endring(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val personident: Personident,
        override val kandidat: Boolean,
        override val arsak: Arsak,
    ) : DialogmotekandidatEndring()

    data class Unntak(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val personident: Personident,
        val createdBy: String,
        val unntakArsak: Arsak,
        val beskrivelse: String?,
    ) : DialogmotekandidatEndring() {
        override val kandidat: Boolean = false
        override val arsak: DialogmotekandidatEndring.Arsak = DialogmotekandidatEndring.Arsak.UNNTAK

        enum class Arsak {
            MEDISINSKE_GRUNNER,
            INNLEGGELSE_INSTITUSJON,

            @Deprecated("Brukes ikke lenger for Unntak, bruk IkkeAktuell")
            FRISKMELDT,
            FORVENTET_FRISKMELDING_INNEN_28UKER,
            DOKUMENTERT_TILTAK_FRISKMELDING,

            @Deprecated("Brukes ikke lenger for Unntak, bruk IkkeAktuell")
            ARBEIDSFORHOLD_OPPHORT,
        }
    }

    data class IkkeAktuell(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val personident: Personident,
        val createdBy: String,
        val ikkeAktuellArsak: Arsak,
        val beskrivelse: String?,
    ) : DialogmotekandidatEndring() {
        override val kandidat: Boolean = false
        override val arsak: DialogmotekandidatEndring.Arsak = DialogmotekandidatEndring.Arsak.IKKE_AKTUELL

        enum class Arsak {
            ARBEIDSTAKER_AAP,
            ARBEIDSTAKER_DOD,
            DIALOGMOTE_AVHOLDT,
            FRISKMELDT,
            ARBEIDSFORHOLD_OPPHORT,
        }
    }

    companion object {
        fun stoppunktKandidat(personident: Personident) =
            create(
                personIdentNumber = personident,
                kandidat = true,
                arsak = Arsak.STOPPUNKT,
            )

        fun ferdigstiltDialogmote(personident: Personident) =
            create(
                personIdentNumber = personident,
                kandidat = false,
                arsak = Arsak.DIALOGMOTE_FERDIGSTILT,
            )

        fun lukketDialogmote(personident: Personident) =
            create(
                personIdentNumber = personident,
                kandidat = false,
                arsak = Arsak.DIALOGMOTE_LUKKET,
            )

        fun unntak(
            personident: Personident,
            createdBy: String,
            arsak: Unntak.Arsak,
            beskrivelse: String?,
        ): Unntak {
            if (arsak in listOf(Unntak.Arsak.FRISKMELDT, Unntak.Arsak.ARBEIDSFORHOLD_OPPHORT)) {
                throw IllegalArgumentException("$arsak skal ikke brukes for nye unntak, bruk IkkeAktuell")
            }
            return Unntak(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                personident = personident,
                createdBy = createdBy,
                unntakArsak = arsak,
                beskrivelse = beskrivelse,
            )
        }

        fun ikkeAktuell(
            personident: Personident,
            createdBy: String,
            arsak: IkkeAktuell.Arsak,
            beskrivelse: String?,
        ) = IkkeAktuell(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personident = personident,
            createdBy = createdBy,
            ikkeAktuellArsak = arsak,
            beskrivelse = beskrivelse,
        )

        fun lukket(personIdentNumber: Personident) =
            create(
                personIdentNumber = personIdentNumber,
                kandidat = false,
                arsak = Arsak.LUKKET,
            )

        private fun create(
            personIdentNumber: Personident,
            kandidat: Boolean,
            arsak: Arsak,
        ) = Endring(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            personident = personIdentNumber,
            kandidat = kandidat,
            arsak = arsak,
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
