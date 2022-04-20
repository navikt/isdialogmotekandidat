package no.nav.syfo.dialogmotestatusendring.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.application.kafka.ApplicationKafkaEnvironment
import no.nav.syfo.application.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun kafkaDialogmoteStatusEndringConsumerConfig(
    applicationKafkaEnvironment: ApplicationKafkaEnvironment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConsumerConfig(applicationKafkaEnvironment))

        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName

        this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = applicationKafkaEnvironment.aivenSchemaRegistryUrl
        this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] = "${applicationKafkaEnvironment.aivenRegistryUser}:${applicationKafkaEnvironment.aivenRegistryPassword}"
        this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}
