package no.nav.syfo.infrastructure.cronjob.dialogmotekandidat

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.cronjob.Cronjob
import no.nav.syfo.infrastructure.cronjob.CronjobResult
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.domain.DialogmotekandidatEndring
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class DialogmotekandidatOutdatedCronjob(
    private val outdatedDialogmotekandidatCutoff: LocalDate,
    private val dialogmotekandidatService: DialogmotekandidatService,
) : Cronjob {
    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        runJob()
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()

        val cutoff = outdatedDialogmotekandidatCutoff.atStartOfDay()
        val outdatedDialogmotekandidater = dialogmotekandidatService.getOutdatedDialogmotekandidater(cutoff)
        val withGivenUuids = uuids.mapNotNull { dialogmotekandidatService.getDialogmotekandidatEndring(it) }
        val dialogmotekandidaterToBeRemoved = outdatedDialogmotekandidater + withGivenUuids

        dialogmotekandidaterToBeRemoved.forEach {
            try {
                val dialogmotekandidatLukket = DialogmotekandidatEndring.lukket(it.personIdentNumber)
                dialogmotekandidatService.createDialogmotekandidatEndring(dialogmotekandidatLukket)
                result.updated++
            } catch (e: Exception) {
                result.failed++
                log.error("Got exception when creating dialogmotekandidat-endring LUKKET", e)
            }
        }

        log.info(
            "Completed dialogmote-kandidat-outdated job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatOutdatedCronjob::class.java)

        private val uuids = listOf(UUID.fromString("69d4ace5-9e27-497c-ac59-4371b9768aab"))
    }
}
