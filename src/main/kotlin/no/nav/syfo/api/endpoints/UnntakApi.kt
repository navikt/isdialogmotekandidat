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
            val personIdent = Personident(createUnntakDTO.personIdent)
            validateVeilederAccess(
                action = "Create unntak for person",
                personIdentToAccess = personIdent,
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
            val personIdent = personIdentHeader()?.let { personIdent ->
                Personident(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to get unntak for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get unntak for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val unntakDTOList = dialogmotekandidatVurderingService.getUnntakList(
                    personIdent = personIdent
                ).toUnntakDTOList()

                call.respond(unntakDTOList)
            }
        }
    }
}
