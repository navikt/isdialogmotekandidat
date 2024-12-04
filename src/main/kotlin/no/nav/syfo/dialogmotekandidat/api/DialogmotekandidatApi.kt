package no.nav.syfo.dialogmotekandidat.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.domain.toDialogmotekandidatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.ikkeaktuell.IkkeAktuellService
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.unntak.UnntakService
import no.nav.syfo.util.*
import java.time.LocalDate

const val kandidatApiBasePath = "/api/internad/v1/kandidat"
const val kandidatApiPersonidentPath = "/personident"
const val kandidatApiHistorikkPath = "/historikk"

fun Route.registerDialogmotekandidatApi(
    dialogmotekandidatService: DialogmotekandidatService,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    unntakService: UnntakService,
    ikkeAktuellService: IkkeAktuellService,
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
                    veilederToken = call.getBearerHeader(),
                    callId = call.getCallId(),
                )
                val latestKandidatEndring = dialogmotekandidatService.getLatestDialogmotekandidatEndring(
                    personIdent = personIdent
                )
                val kandidatDate = latestKandidatEndring?.createdAt?.toLocalDate()
                val oppfolgingstilfelleStart = oppfolgingstilfelle?.tilfelleStart
                val kandidatInOppfolgingstilfelle = oppfolgingstilfelleStart != null && kandidatDate != null && kandidatDate.isAfterOrEqual(oppfolgingstilfelleStart)
                val sevenDaysPassedSinceKandidat = kandidatDate != null && kandidatDate.isBeforeOrEqual(LocalDate.now().minusDays(7))

                val kandidatDTO = if (kandidatInOppfolgingstilfelle && sevenDaysPassedSinceKandidat) {
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
        get(kandidatApiHistorikkPath) {
            val personident = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to get historikk for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get historikk for person",
                personIdentToAccess = personident,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val dialogmotekandidatEndringer = dialogmotekandidatService.getDialogmotekandidatEndringer(
                    personIdent = personident,
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
