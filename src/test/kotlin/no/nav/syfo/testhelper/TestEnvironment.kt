package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureOpenIdTokenEndpoint: String,
    syfoTilgangskontrollUrl: String,
    oppfolgingstilfelleUrl: String,
) = Environment(
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
        openidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
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
    clients = ClientsEnvironment(
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = syfoTilgangskontrollUrl,
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
        ),
        oppfolgingstilfelle = ClientEnvironment(
            baseUrl = oppfolgingstilfelleUrl,
            clientId = "dev-gcp.teamsykefravr.isoppfolgingstilfelle",
        ),
    ),
    toggleKafkaConsumerIdenthendelseEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
