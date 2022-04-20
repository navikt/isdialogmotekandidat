package no.nav.syfo.application.kafka

data class KafkaEnvironment(
    val aivenBootstrapServers: String,
    val aivenCredstorePassword: String,
    val aivenKeystoreLocation: String,
    val aivenSecurityProtocol: String,
    val aivenTruststoreLocation: String,
    val aivenSchemaRegistryUrl: String,
    val aivenRegistryUser: String,
    val aivenRegistryPassword: String,
)
