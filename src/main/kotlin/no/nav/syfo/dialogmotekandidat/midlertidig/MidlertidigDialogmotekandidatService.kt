package no.nav.syfo.dialogmotekandidat.midlertidig

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat
import org.slf4j.LoggerFactory
import java.sql.Connection

class MidlertidigDialogmotekandidatService(
    private val database: DatabaseInterface,
    private val midlertidigDialogmotekandidatEndringProducer: MidlertidigDialogmotekandidatEndringProducer,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val latestOppfolgingstilfelle = oppfolgingstilfelleService.getSisteOppfolgingstilfelle(
            arbeidstakerPersonIdent = dialogmotekandidatStoppunkt.personIdent
        )
            ?: throw RuntimeException("No Oppfolgingstilfelle found for dialogmote-kandidat-stoppunkt with uuid: ${dialogmotekandidatStoppunkt.uuid}")

        database.connection.use { connection ->
            val dialogmotekandidatEndringList = connection.getMidlertidigDialogmotekandidatEndringListForPerson(
                personIdent = dialogmotekandidatStoppunkt.personIdent
            ).toDialogmotekandidatEndringList()

            val status =
                if (latestOppfolgingstilfelle.isDialogmotekandidat(dialogmotekandidatEndringList = dialogmotekandidatEndringList)) DialogmotekandidatStoppunktStatus.KANDIDAT
                else DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT

            connection.updateDialogmotekandidatStoppunktStatus(
                uuid = dialogmotekandidatStoppunkt.uuid,
                status = status,
            )

            if (status == DialogmotekandidatStoppunktStatus.KANDIDAT) {
                val newMidlertidigDialogmotekandidatEndring = dialogmotekandidatStoppunkt.toDialogmotekandidatEndring()
                createMidlertidigDialogmotekandidatEndring(
                    connection = connection,
                    dialogmotekandidatEndring = newMidlertidigDialogmotekandidatEndring,
                )
                COUNT_MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING.increment()
            } else {
                COUNT_MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING.increment()
                log.info("Processed ${DialogmotekandidatStoppunkt::class.java.simpleName}, not kandidat - no MidlertidigDialogmotekandidatEndring created")
            }

            connection.commit()
        }
    }

    fun createMidlertidigDialogmotekandidatEndring(
        connection: Connection,
        dialogmotekandidatEndring: DialogmotekandidatEndring,
    ) {
        connection.createMidlertidigDialogmotekandidatEndring(
            midlertidigDialogmotekandidatEndring = dialogmotekandidatEndring,
        )
        midlertidigDialogmotekandidatEndringProducer.sendMidlertidigDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MidlertidigDialogmotekandidatService::class.java)
    }
}
