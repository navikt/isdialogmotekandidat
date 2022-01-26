package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.commonKafkaAivenConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun kafkaOppfolgingstilfelleArbeidstakerConsumerConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConfig(applicationEnvironmentKafka))

        this[ConsumerConfig.GROUP_ID_CONFIG] = "isdialogmotekandidat-v1"
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            KafkaOppfolgingstilfelleArbeidstakerDeserializer::class.java.canonicalName
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
        this[ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG] = "" + (10 * 1024 * 1024)
    }
}
