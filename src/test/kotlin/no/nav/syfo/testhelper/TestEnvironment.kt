package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
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
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = "syfoTilgangskontrollUrl",
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
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
