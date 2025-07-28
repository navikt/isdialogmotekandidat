package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePersonDeserializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun kafkaOppfolgingstilfellePersonConsumerConfig(
    kafkaEnvironment: KafkaEnvironment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConsumerConfig(kafkaEnvironment))
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            KafkaOppfolgingstilfellePersonDeserializer::class.java.canonicalName
    }
}

fun commonKafkaAivenConfig(
    kafkaEnvironment: KafkaEnvironment,
) = Properties().apply {
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaEnvironment.aivenBootstrapServers
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = kafkaEnvironment.aivenSecurityProtocol
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaEnvironment.aivenTruststoreLocation
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaEnvironment.aivenKeystoreLocation
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
}

fun commonKafkaAivenConsumerConfig(
    kafkaEnvironment: KafkaEnvironment,
) = Properties().apply {
    putAll(commonKafkaAivenConfig(kafkaEnvironment))
    this[ConsumerConfig.GROUP_ID_CONFIG] = "isdialogmotekandidat-v1"
    this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
    this[ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG] = "" + (10 * 1024 * 1024)
}
