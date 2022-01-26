package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC =
    "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-arbeidstaker"

fun launchKafkaTaskOppfolgingstilfelleArbeidstaker(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    kafkaOppfolgingstilfelleArbeidstakerService: KafkaOppfolgingstilfelleArbeidstakerService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicOppfolgingstilfelleArbeidstaker(
            applicationState = applicationState,
            applicationEnvironmentKafka = applicationEnvironmentKafka,
            kafkaOppfolgingstilfelleArbeidstakerService = kafkaOppfolgingstilfelleArbeidstakerService,
        )
    }
}

fun blockingApplicationLogicOppfolgingstilfelleArbeidstaker(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    kafkaOppfolgingstilfelleArbeidstakerService: KafkaOppfolgingstilfelleArbeidstakerService,
) {
    log.info("Setting up kafka consumer for ${KafkaOppfolgingstilfelleArbeidstaker::class.java.simpleName}")

    val consumerProperties = kafkaOppfolgingstilfelleArbeidstakerConsumerConfig(
        applicationEnvironmentKafka = applicationEnvironmentKafka,
    )
    val kafkaConsumerOppfolgingstilfelleArbeidstaker =
        KafkaConsumer<String, KafkaOppfolgingstilfelleArbeidstaker>(consumerProperties)

    kafkaConsumerOppfolgingstilfelleArbeidstaker.subscribe(
        listOf(OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC)
    )
    while (applicationState.ready) {
        kafkaOppfolgingstilfelleArbeidstakerService.pollAndProcessRecords(
            kafkaConsumerOppfolgingstilfelleArbeidstaker = kafkaConsumerOppfolgingstilfelleArbeidstaker,
        )
    }
}
