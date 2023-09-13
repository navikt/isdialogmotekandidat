package no.nav.syfo.cronjob

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.cronjob.dialogmotekandidat.DialogmotekandidatOutdatedCronjob
import no.nav.syfo.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    dialogmotekandidatService: DialogmotekandidatService,
) {
    val cronjobs = mutableListOf<Cronjob>()
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
        dialogmotekandidatService = dialogmotekandidatService,
        intervalDelayMinutes = environment.stoppunktCronjobDelay,
    )
    cronjobs.add(dialogmotekandidatStoppunktCronjob)

    if (environment.outdatedCronjobEnabled) {
        val dialogmotekandidatOutdatedCronjob = DialogmotekandidatOutdatedCronjob(
            outdatedDialogmotekandidatCutoff = environment.outdatedCutoff,
            dialogmotekandidatService = dialogmotekandidatService,
        )
        cronjobs.add(dialogmotekandidatOutdatedCronjob)
    }

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(
                cronjob = it
            )
        }
    }
}
