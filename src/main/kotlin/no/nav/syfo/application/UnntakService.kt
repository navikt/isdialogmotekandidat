package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndringList
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.isLatestIkkeKandidat
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.createUnntak
import no.nav.syfo.infrastructure.database.toUnntakList
import no.nav.syfo.infrastructure.database.getUnntakList
import no.nav.syfo.domain.Unntak

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
        val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
            arbeidstakerPersonIdent = unntak.personIdent,
            veilederToken = veilederToken,
            callId = callId,
        )
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personIdent = unntak.personIdent)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }

            connection.createUnntak(unntak = unntak)
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
}
