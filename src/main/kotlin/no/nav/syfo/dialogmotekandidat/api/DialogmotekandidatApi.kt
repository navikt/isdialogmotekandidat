package no.nav.syfo.dialogmotekandidat.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.domain.toDialogmotekandidatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*

const val kandidatApiBasePath = "/api/internad/v1/kandidat"
const val kandidatApiPersonidentPath = "/personident"

fun Route.registerDialogmotekandidatApi(
    dialogmotekandidatService: DialogmotekandidatService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(kandidatApiBasePath) {
        get(kandidatApiPersonidentPath) {
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to get kandidat for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get Kandidat for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val kandidatDTO: DialogmotekandidatDTO = dialogmotekandidatService.getLatestDialogmotekandidatEndring(
                    personIdent = personIdent
                ).toDialogmotekandidatDTO()

                call.respond(kandidatDTO)
            }
        }
    }
}
