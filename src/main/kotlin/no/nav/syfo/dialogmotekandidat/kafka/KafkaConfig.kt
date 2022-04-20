package no.nav.syfo.dialogmotekandidat.kafka

import no.nav.syfo.application.kafka.ApplicationKafkaEnvironment
import no.nav.syfo.application.kafka.commonKafkaAivenConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun kafkaDialogmotekandidatEndringProducerConfig(
    kafkaEnvironment: ApplicationKafkaEnvironment
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConfig(kafkaEnvironment))
        this[ProducerConfig.ACKS_CONFIG] = "all"
        this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
        this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
        this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
        this[ProducerConfig.RETRIES_CONFIG] = "100000"
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaDialogmotekandidatEndringSerializer::class.java.canonicalName
    }
}
