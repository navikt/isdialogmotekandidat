package no.nav.syfo.application

import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.isLatestIkkeKandidat
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndringList
import no.nav.syfo.infrastructure.database.toIkkeAktuellList

class IkkeAktuellService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val ikkeAktuellRepository: IIkkeAktuellRepository,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    suspend fun createIkkeAktuell(
        ikkeAktuell: IkkeAktuell,
        veilederToken: String,
        callId: String,
    ) {
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personIdent = ikkeAktuell.personIdent)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create IkkeAktuell: Person is not kandidat")
            }

            ikkeAktuellRepository.createIkkeAktuell(connection = connection, commit = false, ikkeAktuell = ikkeAktuell)
            val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
                arbeidstakerPersonIdent = ikkeAktuell.personIdent,
                veilederToken = veilederToken,
                callId = callId,
            )
            val newDialogmotekandidatEndring = DialogmotekandidatEndring.ikkeAktuell(
                personIdentNumber = ikkeAktuell.personIdent,
            )
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
                unntak = null,
            )
            connection.commit()
        }
    }

    suspend fun getIkkeAktuellList(personIdent: Personident): List<IkkeAktuell> =
        ikkeAktuellRepository.getIkkeAktuellListForPerson(personIdent = personIdent).toIkkeAktuellList()
}
