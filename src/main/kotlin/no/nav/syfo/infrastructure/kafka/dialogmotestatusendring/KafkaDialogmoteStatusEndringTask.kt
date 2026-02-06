package no.nav.syfo.infrastructure.kafka.dialogmotestatusendring

import no.nav.syfo.ApplicationState
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val DIALOGMOTE_STATUS_ENDRING_TOPIC = "teamsykefravr.isdialogmote-dialogmote-statusendring"

fun launchKafkaTaskDialogmoteStatusEndring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    dialogmoteStatusEndringConsumer: DialogmoteStatusEndringConsumer,
) {
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        blockingApplicationLogicDialogmoteStatusEndring(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            dialogmoteStatusEndringConsumer = dialogmoteStatusEndringConsumer
        )
    }
}

fun blockingApplicationLogicDialogmoteStatusEndring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    dialogmoteStatusEndringConsumer: DialogmoteStatusEndringConsumer,
) {
    log.info("Setting up kafka consumer for ${KDialogmoteStatusEndring::class.java.simpleName}")

    val kafkaConsumerDialogmoteStatusEndring = KafkaConsumer<String, KDialogmoteStatusEndring>(
        DialogmoteStatusEndringConsumer.config(
            kafkaEnvironment = kafkaEnvironment
        )
    )

    kafkaConsumerDialogmoteStatusEndring.subscribe(
        listOf(DIALOGMOTE_STATUS_ENDRING_TOPIC)
    )

    while (applicationState.ready) {
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(
            consumer = kafkaConsumerDialogmoteStatusEndring
        )
    }
}
