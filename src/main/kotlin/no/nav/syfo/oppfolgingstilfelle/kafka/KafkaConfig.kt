package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.kafka.ApplicationKafkaEnvironment
import no.nav.syfo.application.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun kafkaOppfolgingstilfellePersonConsumerConfig(
    applicationKafkaEnvironment: ApplicationKafkaEnvironment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConsumerConfig(applicationKafkaEnvironment))
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            KafkaOppfolgingstilfellePersonDeserializer::class.java.canonicalName
    }
}
