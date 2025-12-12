package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.CreateUnntakDTO
import no.nav.syfo.api.toUnntak
import no.nav.syfo.api.toUnntakDTOList
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

const val unntakApiBasePath = "/api/internad/v1/unntak"
const val unntakApiPersonidentPath = "/personident"

fun Route.registerUnntakApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
) {
    route(unntakApiBasePath) {
        post(unntakApiPersonidentPath) {
            val createUnntakDTO = call.receive<CreateUnntakDTO>()
            val personident = Personident(createUnntakDTO.personident)
            validateVeilederAccess(
                action = "Create unntak for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val unntak = createUnntakDTO.toUnntak(
                    createdByIdent = call.getNAVIdent()
                )
                dialogmotekandidatVurderingService.createUnntak(
                    unntak = unntak,
                    veilederToken = getBearerHeader()!!,
                    callId = getCallId(),
                )

                call.respond(HttpStatusCode.Created)
            }
        }
        get(unntakApiPersonidentPath) {
            val personident = personIdentHeader()?.let { personident ->
                Personident(personident)
            }
                ?: throw IllegalArgumentException("Failed to get unntak for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get unntak for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val unntakDTOList = dialogmotekandidatVurderingService.getUnntakList(
                    personident = personident
                ).toUnntakDTOList()

                call.respond(unntakDTOList)
            }
        }
    }
}
