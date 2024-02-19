package no.nav.syfo.dialogmotekandidat.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.domain.toDialogmotekandidatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.util.*
import java.time.LocalDate

const val kandidatApiBasePath = "/api/internad/v1/kandidat"
const val kandidatApiPersonidentPath = "/personident"

fun Route.registerDialogmotekandidatApi(
    dialogmotekandidatService: DialogmotekandidatService,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
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
                val oppfolgingstilfelle = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
                    arbeidstakerPersonIdent = personIdent,
                    veilederToken = getBearerHeader(),
                    callId = getCallId(),
                )
                val latestKandidatEndring = dialogmotekandidatService.getLatestDialogmotekandidatEndring(
                    personIdent = personIdent
                )
                val kandidatDate = latestKandidatEndring?.createdAt?.toLocalDate()
                val oppfolgingstilfelleStart = oppfolgingstilfelle?.tilfelleStart

                val kandidatDTO = if (kandidatDate != null && oppfolgingstilfelleStart != null &&
                    kandidatDate.isAfterOrEqual(oppfolgingstilfelleStart) &&
                    kandidatDate.isBeforeOrEqual(LocalDate.now().minusDays(7))
                ) {
                    latestKandidatEndring.toDialogmotekandidatDTO()
                } else {
                    DialogmotekandidatDTO(
                        kandidat = false,
                        kandidatAt = null,
                    )
                }
                call.respond(kandidatDTO)
            }
        }
    }
}
