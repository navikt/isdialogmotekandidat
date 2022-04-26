package no.nav.syfo.unntak

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring

class UnntakService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
) {
    fun createUnntak(unntak: NewUnntak) {
        val newDialogmotekandidatEndring = DialogmotekandidatEndring.unntak(personIdentNumber = unntak.personIdent)

        database.connection.use { connection ->
            // TODO: Persist unntak

            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring
            )
            connection.commit()
        }
    }
}
