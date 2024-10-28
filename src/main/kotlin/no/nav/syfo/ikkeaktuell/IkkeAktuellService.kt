package no.nav.syfo.ikkeaktuell

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndringList
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.ikkeaktuell.database.IkkeAktuellRepository
import no.nav.syfo.ikkeaktuell.database.createIkkeAktuell
import no.nav.syfo.ikkeaktuell.database.toIkkeAktuellList
import no.nav.syfo.ikkeaktuell.domain.IkkeAktuell
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService

class IkkeAktuellService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val ikkeAktuellRepository: IkkeAktuellRepository,
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

            connection.createIkkeAktuell(ikkeAktuell)
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

    fun getIkkeAktuellList(personIdent: PersonIdentNumber): List<IkkeAktuell> =
        ikkeAktuellRepository.getIkkeAktuellListForPerson(personIdent = personIdent).toIkkeAktuellList()
}
