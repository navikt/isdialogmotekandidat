package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.kafka.ApplicationKafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val OPPFOLGINGSTILFELLE_PERSON_TOPIC =
    "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person"

fun launchKafkaTaskOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    applicationKafkaEnvironment: ApplicationKafkaEnvironment,
    kafkaOppfolgingstilfellePersonService: KafkaOppfolgingstilfellePersonService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicOppfolgingstilfellePerson(
            applicationState = applicationState,
            applicationKafkaEnvironment = applicationKafkaEnvironment,
            kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
        )
    }
}

fun blockingApplicationLogicOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    applicationKafkaEnvironment: ApplicationKafkaEnvironment,
    kafkaOppfolgingstilfellePersonService: KafkaOppfolgingstilfellePersonService,
) {
    log.info("Setting up kafka consumer for ${KafkaOppfolgingstilfellePerson::class.java.simpleName}")

    val consumerProperties = kafkaOppfolgingstilfellePersonConsumerConfig(
        applicationKafkaEnvironment = applicationKafkaEnvironment,
    )
    val kafkaConsumerOppfolgingstilfellePerson =
        KafkaConsumer<String, KafkaOppfolgingstilfellePerson>(consumerProperties)

    kafkaConsumerOppfolgingstilfellePerson.subscribe(
        listOf(OPPFOLGINGSTILFELLE_PERSON_TOPIC)
    )
    while (applicationState.ready) {
        kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
            kafkaConsumerOppfolgingstilfellePerson = kafkaConsumerOppfolgingstilfellePerson,
        )
    }
}
