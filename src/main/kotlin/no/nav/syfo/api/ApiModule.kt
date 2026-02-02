package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.auth.JwtIssuer
import no.nav.syfo.api.auth.JwtIssuerType
import no.nav.syfo.api.auth.installJwtAuthentication
import no.nav.syfo.api.endpoints.registerAvventApi
import no.nav.syfo.api.endpoints.registerDialogmotekandidatApi
import no.nav.syfo.api.endpoints.registerIkkeAktuellApi
import no.nav.syfo.api.endpoints.registerUnntakApi
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DatabaseInterface

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    dialogmotekandidatService: DialogmotekandidatService,
    dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
        ),
    )
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerDialogmotekandidatApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                dialogmotekandidatService = dialogmotekandidatService,
                dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
            )
            registerUnntakApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
            )
            registerIkkeAktuellApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
            )
            registerAvventApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
            )
        }
    }
}
