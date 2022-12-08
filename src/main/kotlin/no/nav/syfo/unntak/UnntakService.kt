package no.nav.syfo.unntak

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndringList
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.unntak.database.*
import no.nav.syfo.unntak.database.domain.toUnntakList
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.unntak.domain.UnntakArsak
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.LocalDate

class UnntakService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
) {
    suspend fun createUnntak(unntak: Unntak) {
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personIdent = unntak.personIdent)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }

            connection.createUnntak(unntak = unntak)
            val latestOppfolgingstilfelleArbeidstaker = dialogmotekandidatService.getLatestOppfolgingstilfelle(
                personIdentNumber = unntak.personIdent,
            )
            val newDialogmotekandidatEndring = DialogmotekandidatEndring.unntak(
                personIdentNumber = unntak.personIdent,
            )
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.start,
                unntak = unntak,
            )
            connection.commit()
        }
    }

    fun getUnntakList(personIdent: PersonIdentNumber): List<Unntak> {
        return database.getUnntakList(personIdent = personIdent).toUnntakList()
    }

    fun getHackaton(veilderIdent: String): List<HackatonResponse> {
        val forvFrisk = database.getUnntakForVeileder(veilderIdent).toUnntakList().filter { unntak ->
            unntak.arsak == UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER
        }
        return forvFrisk.mapNotNull { unntak ->
            val unntakDato = unntak.createdAt.toLocalDateTimeOslo().toLocalDate()
            val tilfeller = database.getOppfolgingstilfelleArbeidstakerList(unntak.personIdent)
                .toOppfolgingstilfelleArbeidstakerList()
            val tilfelle = tilfeller.find { tilfelle ->
                tilfelle.tilfelleStart.isBefore(unntakDato)
            }
            tilfelle?.let {
                HackatonResponse(
                    unntakDato = unntakDato,
                    tilfelleStart = tilfelle.tilfelleStart,
                    tilfelleEnd = tilfelle.tilfelleEnd,
                )
            }
        }
    }
}

data class HackatonResponse(
    val unntakDato: LocalDate,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
)
