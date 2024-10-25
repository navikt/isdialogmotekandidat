package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.auth.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.api.registerDialogmotekandidatApi
import no.nav.syfo.ikkeaktuell.IkkeAktuellService
import no.nav.syfo.ikkeaktuell.api.registerIkkeAktuellApi
import no.nav.syfo.ikkeaktuell.database.IkkeAktuellRepository
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.unntak.UnntakService
import no.nav.syfo.unntak.api.registerUnntakApi

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
