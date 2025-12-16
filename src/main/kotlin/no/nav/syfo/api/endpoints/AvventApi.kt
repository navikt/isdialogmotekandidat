package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.CreateAvventDTO
import no.nav.syfo.api.toAvvent
import no.nav.syfo.api.toAvventDTOList
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

const val avventApiBasePath = "/api/internad/v1/avvent"
const val avventApiPersonidentPath = "/personident"

fun Route.registerAvventApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
) {
    route(avventApiBasePath) {
        post(avventApiPersonidentPath) {
            val createAvventDTO = call.receive<CreateAvventDTO>()
            val personident = Personident(createAvventDTO.personIdent)
            validateVeilederAccess(
                action = "Create avvent for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val avvent = createAvventDTO.toAvvent(
                    createdByIdent = call.getNAVIdent()
                )
                dialogmotekandidatVurderingService.createAvvent(
                    avvent = avvent,
                )

                call.respond(HttpStatusCode.Created)
            }
        }
        get(avventApiPersonidentPath) {
            val personident = personIdentHeader()?.let { personident ->
                Personident(personident)
            }
                ?: throw IllegalArgumentException("Failed to get avvent for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get avvent for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val avventDTOList = dialogmotekandidatVurderingService.getAvvent(
                    personident = personident
                ).toAvventDTOList()

                call.respond(avventDTOList)
            }
        }
    }
}
