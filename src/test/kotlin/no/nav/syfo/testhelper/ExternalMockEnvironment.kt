package no.nav.syfo.testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.clients.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DialogmotekandidatVurderingRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.testhelper.mock.mockHttpClient
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
    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val azureAdClient = AzureAdClient(environment.azure, mockHttpClient)
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.oppfolgingstilfelle,
        httpClient = mockHttpClient,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfelleClient)

    val dialogmotekandidatRepository = DialogmotekandidatRepository(database = database)
    val dialogmotekandidatVurderingRepository = DialogmotekandidatVurderingRepository(database = database)

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
