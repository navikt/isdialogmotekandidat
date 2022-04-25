package no.nav.syfo.unntak.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val unntakApiBasePath = "/api/internad/v1/unntak"
const val unntakApiPersonidentPath = "/personident"

fun Route.registerUnntakApi() {
    route(unntakApiBasePath) {
        post(unntakApiPersonidentPath) {
            call.respond(HttpStatusCode.Created)
        }
    }
}
