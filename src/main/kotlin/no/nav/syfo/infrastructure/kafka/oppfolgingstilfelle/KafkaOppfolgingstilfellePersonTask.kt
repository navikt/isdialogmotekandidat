package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val OPPFOLGINGSTILFELLE_PERSON_TOPIC =
    "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person"

fun launchKafkaTaskOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    oppfolgingstilfellePersonConsumer: OppfolgingstilfellePersonConsumer,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            oppfolgingstilfellePersonConsumer = oppfolgingstilfellePersonConsumer,
        )
    }
}

fun blockingApplicationLogicOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    oppfolgingstilfellePersonConsumer: OppfolgingstilfellePersonConsumer,
) {
    log.info("Setting up kafka consumer for ${KafkaOppfolgingstilfellePerson::class.java.simpleName}")

    val kafkaConsumerOppfolgingstilfellePerson =
        KafkaConsumer<String, KafkaOppfolgingstilfellePerson>(OppfolgingstilfellePersonConsumer.config(kafkaEnvironment = kafkaEnvironment))

    kafkaConsumerOppfolgingstilfellePerson.subscribe(
        listOf(OPPFOLGINGSTILFELLE_PERSON_TOPIC)
    )
    while (applicationState.ready) {
        oppfolgingstilfellePersonConsumer.pollAndProcessRecords(
            consumer = kafkaConsumerOppfolgingstilfellePerson,
        )
    }
}
