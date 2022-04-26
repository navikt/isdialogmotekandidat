package no.nav.syfo.unntak.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.api.domain.NewUnntakDTO
import no.nav.syfo.util.*

const val unntakApiBasePath = "/api/internad/v1/unntak"
const val unntakApiPersonidentPath = "/personident"

fun Route.registerUnntakApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(unntakApiBasePath) {
        post(unntakApiPersonidentPath) {
            val newUnntakDTO = call.receive<NewUnntakDTO>()
            val personIdent = PersonIdentNumber(newUnntakDTO.personIdent)
            validateVeilederAccess(
                action = "Create unntak for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}
