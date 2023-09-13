package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
) {
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = externalMockEnvironment.database,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatService = dialogmotekandidatService,
        veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = externalMockEnvironment.environment.clients.syfotilgangskontroll,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )
}
