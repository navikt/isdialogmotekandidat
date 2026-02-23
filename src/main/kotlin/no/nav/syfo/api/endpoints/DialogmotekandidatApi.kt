package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.AvventDTO
import no.nav.syfo.api.DialogmotekandidatResponseDTO
import no.nav.syfo.api.GetDialogmotekandidatForPersonsResponseDTO
import no.nav.syfo.api.GetDialogmotekandidaterRequestDTO
import no.nav.syfo.api.HistorikkDTO
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.personIdentHeader
import no.nav.syfo.util.validateVeilederAccess

const val kandidatApiBasePath = "/api/internad/v1/kandidat"
const val kandidatApiPersonidentPath = "/personident"
const val kandidatApiHistorikkPath = "/historikk"

fun Route.registerDialogmotekandidatApi(
    dialogmotekandidatService: DialogmotekandidatService,
    dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(kandidatApiBasePath) {
        get(kandidatApiPersonidentPath) {
            val personident = personIdentHeader()?.let { personident ->
                Personident(personident)
            }
                ?: throw IllegalArgumentException("Failed to get kandidat for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get Kandidat for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val dialogmotekandidat = dialogmotekandidatService.getKandidat(
                    personident = personident,
                    veilederToken = getBearerHeader(),
                    callId = getCallId(),
                )
                call.respond(dialogmotekandidat)
            }
        }
        get(kandidatApiHistorikkPath) {
            val personident = personIdentHeader()?.let { personident ->
                Personident(personident)
            }
                ?: throw IllegalArgumentException("Failed to get historikk for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get historikk for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val dialogmotekandidatEndringer = dialogmotekandidatService.getDialogmotekandidatEndringer(
                    personident = personident,
                )
                val unntak = dialogmotekandidatVurderingService.getUnntakList(personident = personident)
                val ikkeAktuell = dialogmotekandidatVurderingService.getIkkeAktuellList(personident = personident)
                val historikkDTOs =
                    HistorikkDTO.fromDialogmotekandidatEndringer(dialogmotekandidatEndringer = dialogmotekandidatEndringer + unntak + ikkeAktuell)
                call.respond(historikkDTOs)
            }
        }

        post("/get-kandidater") {
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to get vurderinger for personer. No Authorization header supplied.")
            val requestBody = call.receive<GetDialogmotekandidaterRequestDTO>()
            val personidenter = requestBody.personidenter.map { Personident(it) }

            val personerVeilederHasAccessTo = veilederTilgangskontrollClient.veilederPersonerAccess(
                personidenter = personidenter,
                token = token,
                callId = call.getCallId(),
            )
            if (personerVeilederHasAccessTo.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                val kandidater = dialogmotekandidatService.getDialogmotekandidater(personerVeilederHasAccessTo)

                if (kandidater.isEmpty()) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    val responseDTO = GetDialogmotekandidatForPersonsResponseDTO(
                        dialogmotekandidater = kandidater.entries.associate { (personident, dialogmotekandidatEndring) ->
                            personident.value to DialogmotekandidatResponseDTO(
                                uuid = dialogmotekandidatEndring.uuid,
                                createdAt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                                personident = dialogmotekandidatEndring.personident.value,
                                isKandidat = dialogmotekandidatEndring.kandidat,
                                avvent = if (dialogmotekandidatEndring is DialogmotekandidatEndring.Avvent) {
                                    AvventDTO(
                                        uuid = dialogmotekandidatEndring.uuid.toString(),
                                        createdAt = dialogmotekandidatEndring.createdAt.toLocalDateTime(),
                                        frist = dialogmotekandidatEndring.frist,
                                        createdBy = dialogmotekandidatEndring.createdBy,
                                        personident = dialogmotekandidatEndring.personident.value,
                                        beskrivelse = dialogmotekandidatEndring.beskrivelse,
                                    )
                                } else {
                                    null
                                }
                            )
                        }
                    )
                    call.respond(responseDTO)
                }
            }
        }
    }
}
