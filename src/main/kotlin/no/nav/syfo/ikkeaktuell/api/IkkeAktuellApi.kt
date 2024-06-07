package no.nav.syfo.ikkeaktuell.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.ikkeaktuell.IkkeAktuellService
import no.nav.syfo.ikkeaktuell.api.domain.CreateIkkeAktuellDTO
import no.nav.syfo.ikkeaktuell.api.domain.toIkkeAktuell
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
