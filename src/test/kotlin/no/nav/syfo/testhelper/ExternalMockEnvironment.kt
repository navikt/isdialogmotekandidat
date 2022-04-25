package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.testhelper.mock.AzureADMock
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    private val azureAdMock = AzureADMock()
    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
    )

    val environment: Environment by lazy {
        testEnvironment(
            kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            azureOpenIdTokenEndpoint = azureAdMock.getUrl(),
        )
    }

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.embeddedEnvironment.start()
    this.externalMocks.forEach { (_, externalMock) -> externalMock.start() }
}
