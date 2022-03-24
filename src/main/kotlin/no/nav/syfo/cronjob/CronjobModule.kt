package no.nav.syfo.cronjob

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.cronjob.dialogmotekandidat.DialogmotekandidatStoppunktCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.kafkaDialogmotekandidatEndringProducerConfig
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import org.apache.kafka.clients.producer.KafkaProducer

fun launchCronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = KafkaProducer(
            kafkaDialogmotekandidatEndringProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = database
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        database = database,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
    )
    val dialogmotekandidatStoppunktCronjob = DialogmotekandidatStoppunktCronjob(
        dialogmotekandidatService = dialogmotekandidatService
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(
            cronjob = dialogmotekandidatStoppunktCronjob
        )
    }
}
