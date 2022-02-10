package no.nav.syfo.testhelper

import no.nav.syfo.application.*

fun testEnvironment(
    kafkaBootstrapServers: String,
) = Environment(
    isdialogmotekandidatDbHost = "localhost",
    isdialogmotekandidatDbPort = "5432",
    isdialogmotekandidatDbName = "isoppfolgingstilfelle_dev",
    isdialogmotekandidatDbUsername = "username",
    isdialogmotekandidatDbPassword = "password",
    electorPath = "/tmp",
    kafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
    ),
    kafkaOppfolgingstilfellePersonProcessingEnabled = true,
    dialogmotekandidatStoppunktCronjobEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
