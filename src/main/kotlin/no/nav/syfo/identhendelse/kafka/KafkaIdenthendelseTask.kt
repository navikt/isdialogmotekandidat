package no.nav.syfo.identhendelse.kafka

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.application.kafka.KafkaEnvironment
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaIdenthendelseConsumerService: IdenthendelseConsumerService,
) {
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        val kafkaConfig = kafkaIdenthendelseConsumerConfig(kafkaEnvironment)
        val kafkaConsumer = KafkaConsumer<String, GenericRecord>(kafkaConfig)

        kafkaConsumer.subscribe(
            listOf(PDL_AKTOR_TOPIC)
        )
        while (applicationState.ready) {
            runBlocking {
                kafkaIdenthendelseConsumerService.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
            }
        }
    }
}
