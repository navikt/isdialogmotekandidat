package no.nav.syfo.api.endpoints

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.HistorikkDTO
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.IkkeAktuellService
import no.nav.syfo.application.UnntakService
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

const val kandidatApiBasePath = "/api/internad/v1/kandidat"
const val kandidatApiPersonidentPath = "/personident"
const val kandidatApiHistorikkPath = "/historikk"

fun Route.registerDialogmotekandidatApi(
    dialogmotekandidatService: DialogmotekandidatService,
    unntakService: UnntakService,
    ikkeAktuellService: IkkeAktuellService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(kandidatApiBasePath) {
        get(kandidatApiPersonidentPath) {
            val personident = personIdentHeader()?.let { personIdent ->
                Personident(personIdent)
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
            val personident = personIdentHeader()?.let { personIdent ->
                Personident(personIdent)
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
                val unntak = unntakService.getUnntakList(
                    personIdent = personident,
                )
                val ikkeAktuell = ikkeAktuellService.getIkkeAktuellList(
                    personIdent = personident,
                )
                val historikkDTOs = HistorikkDTO.createHistorikkDTOs(
                    dialogmotekandidatEndringer = dialogmotekandidatEndringer,
                    unntak = unntak,
                    ikkeAktuell = ikkeAktuell,
                )
                call.respond(historikkDTOs)
            }
        }
    }
}
