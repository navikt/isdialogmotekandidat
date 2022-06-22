package no.nav.syfo.cronjob

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    dialogmotekandidatService: DialogmotekandidatService,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
        dialogmotekandidatService = dialogmotekandidatService
    )

    /*
    Without changes, only one StoppunktCronjob can be enabled at time.
    When Dialogmotekandidat is ready for production, there are two options:
       1. Enable dialogmotekandidatStoppunktCronjob and disable midlertidigDialogmotekandidatStoppunktCronjob(with this option, Syfomotebehov change the topic it is consuming at the same day as the switch is made).
       2. Change the code to support enabling of both dialogmotekandidatStoppunktCronjob and midlertidigDialogmotekandidatStoppunktCronjob simultaneously.
     */
    if (environment.dialogmotekandidatStoppunktCronjobEnabled || environment.midlertidigDialogmotekandidatStoppunktCronjobEnabled) {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(
                cronjob = dialogmotekandidatStoppunktCronjob
            )
        }
    }
}
