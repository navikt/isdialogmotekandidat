package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat
import java.sql.Connection

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    private val database: DatabaseInterface,
) {
    fun getDialogmotekandidatMedStoppunktPlanlagtTodayList(): List<DialogmotekandidatStoppunkt> =
        database.getDialogmotekandidatStoppunktTodayList().toDialogmotekandidatStoppunktList()

    fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val oppfolgingstilfelle = oppfolgingstilfelleService.getSisteOppfolgingstilfelle(
            arbeidstakerPersonIdent = dialogmotekandidatStoppunkt.personIdent
        )
            ?: throw RuntimeException("No Oppfolgingstilfelle found for dialogmote-kandidat-stoppunkt with uuid: ${dialogmotekandidatStoppunkt.uuid}")

        val status =
            if (oppfolgingstilfelle.isDialogmotekandidat()) DialogmotekandidatStoppunktStatus.KANDIDAT
            else DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT

        database.connection.use { connection ->
            connection.updateDialogmotekandidatStoppunktStatus(
                uuid = dialogmotekandidatStoppunkt.uuid,
                status = status,
            )

            val latestEndring = connection.getLatestDialogmotekandidatEndringForPerson(
                personIdent = dialogmotekandidatStoppunkt.personIdent
            )?.toDialogmotekandidatEndring()

            if (status == DialogmotekandidatStoppunktStatus.KANDIDAT && latestEndring.ikkeKandidat()) {
                val newDialogmotekandidatEndring = dialogmotekandidatStoppunkt.toDialogmotekandidatEndring()
                createDialogmotekandidatEndring(
                    connection = connection,
                    dialogmotekandidatEndring = newDialogmotekandidatEndring,
                )
            }

            connection.commit()
        }
    }

    fun createDialogmotekandidatEndring(
        connection: Connection,
        dialogmotekandidatEndring: DialogmotekandidatEndring,
    ) {
        connection.createDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring
        )
        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring
        )
    }
}
