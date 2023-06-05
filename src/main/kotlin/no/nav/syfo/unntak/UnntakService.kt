package no.nav.syfo.unntak

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.client.oppfolgingstilfelle.HISTOGRAM_CALL_OPPFOLGINGSTILFELLER_UNNTAK_TIMER
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndringList
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.tilfelleForDate
import no.nav.syfo.unntak.database.*
import no.nav.syfo.unntak.database.domain.toUnntakList
import no.nav.syfo.unntak.domain.*
import no.nav.syfo.util.toLocalDateOslo
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class UnntakService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    suspend fun createUnntak(
        unntak: Unntak,
        veilederToken: String,
        callId: String,
    ) {
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personIdent = unntak.personIdent)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }

            connection.createUnntak(unntak = unntak)
            val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
                arbeidstakerPersonIdent = unntak.personIdent,
                veilederToken = veilederToken,
                callId = callId,
            )
            val newDialogmotekandidatEndring = DialogmotekandidatEndring.unntak(
                personIdentNumber = unntak.personIdent,
            )
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
                unntak = unntak,
            )
            connection.commit()
        }
    }

    fun getUnntakList(personIdent: PersonIdentNumber): List<Unntak> {
        return database.getUnntakList(personIdent = personIdent).toUnntakList()
    }

    suspend fun getUnntakStatistikk(
        unntakList: List<Unntak>,
        token: String,
        callId: String,
    ): List<UnntakStatistikk> {
        val starttime = System.currentTimeMillis()
        val oppfolgingstilfelleForUnntak = getOppfolgingstilfelleForUnntak(
            unntakList = unntakList,
            token = token,
            callId = callId
        )
        val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
        HISTOGRAM_CALL_OPPFOLGINGSTILFELLER_UNNTAK_TIMER.record(duration)

        return oppfolgingstilfelleForUnntak.mapNotNull { (unntak, oppfolgingstilfelle) ->
            oppfolgingstilfelle?.let { tilfelle ->
                UnntakStatistikk(
                    unntakDato = unntak.createdAt.toLocalDateOslo(),
                    tilfelleStart = tilfelle.tilfelleStart,
                    tilfelleEnd = tilfelle.tilfelleEnd,
                )
            }
        }
    }

    private suspend fun getOppfolgingstilfelleForUnntak(
        unntakList: List<Unntak>,
        token: String,
        callId: String,
    ): Map<Unntak, Oppfolgingstilfelle?> {
        val personIdentOppfolgingstilfellerMap = ConcurrentHashMap<PersonIdentNumber, List<Oppfolgingstilfelle>>()

        return unntakList.associateWith { unntak ->
            val tilfellerForUnntakPerson = personIdentOppfolgingstilfellerMap.getOrPut(unntak.personIdent) {
                oppfolgingstilfelleService.getAllOppfolgingstilfeller(
                    arbeidstakerPersonIdent = unntak.personIdent,
                    veilederToken = token,
                    callId = callId,
                )
            }

            val unntakDato = unntak.createdAt.toLocalDateOslo()
            val tilfelleForUnntak = tilfellerForUnntakPerson.tilfelleForDate(unntakDato)
            tilfelleForUnntak
        }
    }

    internal fun getUnntakForVeileder(veilderIdent: String): List<Unntak> =
        database.getUnntakForVeileder(veilederIdent = veilderIdent).toUnntakList()
}
