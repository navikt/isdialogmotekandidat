package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
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
    }
}
