package no.nav.syfo.dialogmotestatusendring.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val DIALOGMOTE_STATUS_ENDRING_TOPIC = "teamsykefravr.isdialogmote-dialogmote-statusendring"

fun launchKafkaTaskDialogmoteStatusEndring(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    kafkaDialogmoteStatusEndringService: KafkaDialogmoteStatusEndringService,
) {
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        blockingApplicationLogicDialogmoteStatusEndring(
            applicationState = applicationState,
            applicationEnvironmentKafka = applicationEnvironmentKafka,
            kafkaDialogmoteStatusEndringService = kafkaDialogmoteStatusEndringService
        )
    }
}

fun blockingApplicationLogicDialogmoteStatusEndring(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    kafkaDialogmoteStatusEndringService: KafkaDialogmoteStatusEndringService,
) {
    log.info("Setting up kafka consumer for ${KDialogmoteStatusEndring::class.java.simpleName}")

    val kafkaConsumerDialogmoteStatusEndring = KafkaConsumer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringConsumerConfig(
            applicationEnvironmentKafka = applicationEnvironmentKafka
        )
    )

    kafkaConsumerDialogmoteStatusEndring.subscribe(
        listOf(DIALOGMOTE_STATUS_ENDRING_TOPIC)
    )

    while (applicationState.ready) {
        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = kafkaConsumerDialogmoteStatusEndring
        )
    }
}
