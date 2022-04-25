package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat
import org.slf4j.LoggerFactory
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

        database.connection.use { connection ->
            val latestEndring = connection.getLatestDialogmotekandidatEndringForPerson(
                personIdent = dialogmotekandidatStoppunkt.personIdent
            )?.toDialogmotekandidatEndring()

            val status =
                if (oppfolgingstilfelle.isDialogmotekandidat(latestEndring = latestEndring)) DialogmotekandidatStoppunktStatus.KANDIDAT
                else DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT

            connection.updateDialogmotekandidatStoppunktStatus(
                uuid = dialogmotekandidatStoppunkt.uuid,
                status = status,
            )

            if (status == DialogmotekandidatStoppunktStatus.KANDIDAT) {
                val newDialogmotekandidatEndring = dialogmotekandidatStoppunkt.toDialogmotekandidatEndring()
                createDialogmotekandidatEndring(
                    connection = connection,
                    dialogmotekandidatEndring = newDialogmotekandidatEndring,
                )
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING.increment()
            } else {
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING.increment()
                log.info("Processed ${DialogmotekandidatStoppunkt::class.java.simpleName}, not kandidat - no DialogmotekandidatEndring created")
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

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatService::class.java)
    }
}
