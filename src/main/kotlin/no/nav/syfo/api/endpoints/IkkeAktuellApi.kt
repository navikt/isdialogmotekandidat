package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.CreateIkkeAktuellDTO
import no.nav.syfo.api.toIkkeAktuell
import no.nav.syfo.application.IkkeAktuellService
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

const val ikkeAktuellApiBasePath = "/api/internad/v1/ikkeaktuell"

fun Route.registerIkkeAktuellApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    ikkeAktuellService: IkkeAktuellService,
) {
    route(ikkeAktuellApiBasePath) {
        post("/personident") {
            val createIkkeAktuellDTO = call.receive<CreateIkkeAktuellDTO>()
            val personIdent = Personident(createIkkeAktuellDTO.personIdent)
            validateVeilederAccess(
                action = "Create ikke-aktuell for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val ikkeAktuell = createIkkeAktuellDTO.toIkkeAktuell(
                    createdByIdent = call.getNAVIdent()
                )
                ikkeAktuellService.createIkkeAktuell(
                    ikkeAktuell = ikkeAktuell,
                    veilederToken = getBearerHeader()!!,
                    callId = getCallId(),
                )

                call.respond(HttpStatusCode.Created)
            }
        }
        get("/personident") {
            val personIdent = personIdentHeader()?.let { Personident(it) }
                ?: throw IllegalArgumentException("Failed to get ikke-aktuell for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get ikke-aktuell for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                call.respond(
                    ikkeAktuellService.getIkkeAktuellList(personIdent = personIdent)
                )
            }
        }
    }
}
