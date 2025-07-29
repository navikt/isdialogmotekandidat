package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.auth.JwtIssuer
import no.nav.syfo.api.auth.JwtIssuerType
import no.nav.syfo.api.auth.installJwtAuthentication
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.wellknown.WellKnown
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.api.endpoints.registerDialogmotekandidatApi
import no.nav.syfo.application.IkkeAktuellService
import no.nav.syfo.api.endpoints.registerIkkeAktuellApi
import no.nav.syfo.infrastructure.database.IkkeAktuellRepository
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.application.UnntakService
import no.nav.syfo.api.endpoints.registerUnntakApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    dialogmotekandidatService: DialogmotekandidatService,
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

    val unntakService = UnntakService(
        database = database,
        dialogmotekandidatService = dialogmotekandidatService,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )
    val ikkeAktuellService = IkkeAktuellService(
        database = database,
        dialogmotekandidatService = dialogmotekandidatService,
        ikkeAktuellRepository = IkkeAktuellRepository(database),
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerDialogmotekandidatApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                dialogmotekandidatService = dialogmotekandidatService,
                unntakService = unntakService,
                ikkeAktuellService = ikkeAktuellService,
            )
            registerUnntakApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                unntakService = unntakService,
            )
            registerIkkeAktuellApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                ikkeAktuellService = ikkeAktuellService,
            )
        }
    }
}
