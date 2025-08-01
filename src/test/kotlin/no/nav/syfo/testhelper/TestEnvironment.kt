package no.nav.syfo.testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment
import java.time.LocalDate

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isdialogmotekandidat_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isdialogmotekandidat-client-id",
        appClientSecret = "isdialogmotekandidat-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    electorPath = "/tmp",
    kafka = KafkaEnvironment(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "isTilgangskontrollUrl",
            clientId = "dev-gcp.teamsykefravr.istilgangskontroll",
        ),
        oppfolgingstilfelle = ClientEnvironment(
            baseUrl = "oppfolgingstilfelleUrl",
            clientId = "dev-gcp.teamsykefravr.isoppfolgingstilfelle",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "pdlClientId",
        ),
    ),
    stoppunktCronjobDelay = 60L * 4,
    outdatedCronjobEnabled = true,
    outdatedCutoff = LocalDate.now().minusYears(1),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
