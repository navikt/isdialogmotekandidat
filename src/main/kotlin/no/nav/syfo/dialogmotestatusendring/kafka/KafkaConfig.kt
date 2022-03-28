package no.nav.syfo.dialogmotestatusendring.kafka

import io.confluent.kafka.serializers.*
import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

fun kafkaDialogmoteStatusEndringConsumerConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConsumerConfig(applicationEnvironmentKafka))
        this[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] =
            applicationEnvironmentKafka.aivenSchemaRegistryUrl
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            KafkaAvroDeserializer::class.java.canonicalName
        this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
            "${applicationEnvironmentKafka.aivenRegistryUser}:${applicationEnvironmentKafka.aivenRegistryPassword}"
        this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}
