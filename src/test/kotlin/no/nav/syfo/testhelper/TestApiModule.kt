package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer

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
        dialogmotekandidatRepository = DialogmotekandidatRepository(externalMockEnvironment.database),
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
            clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )
}
