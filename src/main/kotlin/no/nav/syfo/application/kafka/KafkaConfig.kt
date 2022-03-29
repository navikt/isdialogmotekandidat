package no.nav.syfo.application.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.*

fun commonKafkaAivenConfig(
    kafkaEnvironment: ApplicationEnvironmentKafka,
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
    kafkaEnvironment: ApplicationEnvironmentKafka
) = Properties().apply {
    putAll(commonKafkaAivenConfig(kafkaEnvironment))
    this[ConsumerConfig.GROUP_ID_CONFIG] = "isdialogmotekandidat-v1"
    this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
    this[ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG] = "" + (10 * 1024 * 1024)
}
