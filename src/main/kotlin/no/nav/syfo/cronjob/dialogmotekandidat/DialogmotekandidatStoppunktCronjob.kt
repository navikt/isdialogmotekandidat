package no.nav.syfo.cronjob.dialogmotekandidat

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cronjob.Cronjob
import no.nav.syfo.cronjob.CronjobResult
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import org.slf4j.LoggerFactory

class DialogmotekandidatStoppunktCronjob(
    private val dialogmotekandidatService: DialogmotekandidatService,
) : Cronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 60L * 4

    override suspend fun run() {
        runJob()
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()

        val dialogmotekandidatStoppunktList =
            dialogmotekandidatService.getDialogmotekandidatMedStoppunktPlanlagtTodayList()
        dialogmotekandidatStoppunktList.forEach { dialogmotekandidatStoppunkt ->
            try {
                dialogmotekandidatService.updateDialogmotekandidatStoppunktStatus(dialogmotekandidatStoppunkt)
                result.updated++
                COUNT_CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_UPDATE.increment()
            } catch (e: Exception) {
                log.error("Exception caught while attempting to update dialogmote-kandidat-stoppunkt status", e)
                result.failed++
                COUNT_CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_FAIL.increment()
            }
        }

        log.info(
            "Completed dialogmote-kandidat-stoppunkt job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatStoppunktCronjob::class.java)
    }
}
