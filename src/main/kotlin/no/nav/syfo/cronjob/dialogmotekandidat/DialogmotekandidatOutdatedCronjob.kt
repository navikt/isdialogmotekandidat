package no.nav.syfo.cronjob.dialogmotekandidat

import no.nav.syfo.cronjob.Cronjob
import no.nav.syfo.cronjob.CronjobResult
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DialogmotekandidatOutdatedCronjob(
    private val outdatedDialogmotekandidatCutoff: LocalDate,
    private val dialogmotekandidatService: DialogmotekandidatService,
) : Cronjob {
    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 240

    override suspend fun run() {
        runJob()
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()

        val cutoff = outdatedDialogmotekandidatCutoff.atStartOfDay()
        val outdatedDialogmotekandidater = dialogmotekandidatService.getOutdatedDialogmotekandidater(cutoff)
        outdatedDialogmotekandidater.forEach {
            try {
                val dialogmotekandidatLukket = DialogmotekandidatEndring.lukket(it.personIdentNumber)
                dialogmotekandidatService.createDialogmotekandidatEndring(dialogmotekandidatLukket)
                result.updated++
            } catch (e: Exception) {
                result.failed++
                log.error("Got exception when creating dialogmotekandidat-endring LUKKET", e)
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatOutdatedCronjob::class.java)
    }
}
