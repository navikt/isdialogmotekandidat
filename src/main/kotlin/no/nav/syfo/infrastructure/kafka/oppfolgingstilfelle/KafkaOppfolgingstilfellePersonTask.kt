package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.launchBackgroundTask
import no.nav.syfo.infrastructure.kafka.kafkaOppfolgingstilfellePersonConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val OPPFOLGINGSTILFELLE_PERSON_TOPIC =
    "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person"

fun launchKafkaTaskOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaOppfolgingstilfellePersonService: KafkaOppfolgingstilfellePersonService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
        )
    }
}

fun blockingApplicationLogicOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaOppfolgingstilfellePersonService: KafkaOppfolgingstilfellePersonService,
) {
    log.info("Setting up kafka consumer for ${KafkaOppfolgingstilfellePerson::class.java.simpleName}")

    val consumerProperties = kafkaOppfolgingstilfellePersonConsumerConfig(
        kafkaEnvironment = kafkaEnvironment,
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
