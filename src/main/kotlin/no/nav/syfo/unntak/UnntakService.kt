package no.nav.syfo.unntak

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndringList
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.unntak.database.createUnntak
import no.nav.syfo.unntak.domain.Unntak

class UnntakService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
) {
    fun createUnntak(unntak: Unntak) {
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personIdent = unntak.personIdent)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }

            connection.createUnntak(unntak = unntak)

            val newDialogmotekandidatEndring = DialogmotekandidatEndring.unntak(personIdentNumber = unntak.personIdent)
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring
            )
            connection.commit()
        }
    }
}
