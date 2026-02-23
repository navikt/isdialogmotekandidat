package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.launchBackgroundTask
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatOutdatedCronjob
import no.nav.syfo.infrastructure.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.infrastructure.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.application.DialogmotekandidatService

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
            outdatedDialogmotekandidatCutoffMonths = environment.outdatedCutoffMonths,
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
