package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment

fun testEnvironment(
    kafkaBootstrapServers: String,
) = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isoppfolgingstilfelle_dev",
        username = "username",
        password = "password",
    ),
    electorPath = "/tmp",
    kafka = KafkaEnvironment(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
    ),
    kafkaOppfolgingstilfellePersonProcessingEnabled = true,
    dialogmotekandidatStoppunktCronjobEnabled = true,
    kafkaDialogmoteStatusEndringProcessingEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
