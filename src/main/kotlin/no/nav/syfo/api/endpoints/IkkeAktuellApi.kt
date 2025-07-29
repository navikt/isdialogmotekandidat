package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.CreateIkkeAktuellDTO
import no.nav.syfo.api.toIkkeAktuell
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.application.IkkeAktuellService
import no.nav.syfo.util.*

const val ikkeAktuellApiBasePath = "/api/internad/v1/ikkeaktuell"
const val ikkeAktuellApiPersonidentPath = "/personident"

fun Route.registerIkkeAktuellApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    ikkeAktuellService: IkkeAktuellService,
) {
    route(ikkeAktuellApiBasePath) {
        post(ikkeAktuellApiPersonidentPath) {
            val createIkkeAktuellDTO = call.receive<CreateIkkeAktuellDTO>()
            val personIdent = PersonIdentNumber(createIkkeAktuellDTO.personIdent)
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
    }
}
