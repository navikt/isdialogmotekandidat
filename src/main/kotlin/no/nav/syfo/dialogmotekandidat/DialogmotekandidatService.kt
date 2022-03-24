package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat

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
        database.updateDialogmotekandidatStoppunktStatus(
            uuid = dialogmotekandidatStoppunkt.uuid,
            status = status,
        )

        if (status == DialogmotekandidatStoppunktStatus.KANDIDAT) {
            createDialogmotekandidatEndring(
                dialogmotekandidatEndring = dialogmotekandidatStoppunkt.toDialogmotekandidatEndring()
            )
        }
    }

    private fun createDialogmotekandidatEndring(dialogmotekandidatEndring: DialogmotekandidatEndring) {
        // TODO: Persist dialogmotekandidatEndring
        dialogmotekandidatEndringProducer.sendDialogmotekandidat(
            dialogmotekandidatEndring = dialogmotekandidatEndring
        )
    }
}
