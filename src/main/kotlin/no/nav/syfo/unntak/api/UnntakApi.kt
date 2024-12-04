package no.nav.syfo.unntak.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.UnntakService
import no.nav.syfo.unntak.api.domain.*
import no.nav.syfo.unntak.domain.*
import no.nav.syfo.util.*

const val unntakApiBasePath = "/api/internad/v1/unntak"
const val unntakApiPersonidentPath = "/personident"
const val unntakApiStatistikk = "/statistikk"

fun Route.registerUnntakApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    unntakService: UnntakService,
) {
    route(unntakApiBasePath) {
        post(unntakApiPersonidentPath) {
            val createUnntakDTO = call.receive<CreateUnntakDTO>()
            val personIdent = PersonIdentNumber(createUnntakDTO.personIdent)
            validateVeilederAccess(
                action = "Create unntak for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val unntak = createUnntakDTO.toUnntak(
                    createdByIdent = call.getNAVIdent()
                )
                unntakService.createUnntak(
                    unntak = unntak,
                    veilederToken = getBearerHeader()!!,
                    callId = getCallId(),
                )

                call.respond(HttpStatusCode.Created)
            }
        }
        get(unntakApiPersonidentPath) {
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to get unntak for person: No $NAV_PERSONIDENT_HEADER supplied in request header")
            validateVeilederAccess(
                action = "Get unntak for person",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val unntakDTOList = unntakService.getUnntakList(
                    personIdent = personIdent
                ).toUnntakDTOList()

                call.respond(unntakDTOList)
            }
        }
        get(unntakApiStatistikk) {
            val token = getBearerHeader()!!
            val callId = getCallId()
            val veilderIdent = call.getNAVIdent()

            val unntakForventetFriskmelding = unntakService.getUnntakForVeileder(veilderIdent = veilderIdent)
                .filter { unntak -> unntak.arsak == UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER }
            val personListWithVeilederAccess = veilederTilgangskontrollClient.hasAccessToPersonList(
                personIdentList = unntakForventetFriskmelding.map { it.personIdent }.distinct(),
                token = token,
                callId = callId,
            )
            val unntakForventetFriskmeldingWithVeilederAccess =
                unntakForventetFriskmelding.filter { unntak -> personListWithVeilederAccess.contains(unntak.personIdent) }

            val unntakStatistikkList = unntakService.getUnntakStatistikk(
                unntakList = unntakForventetFriskmeldingWithVeilederAccess,
                token = token,
                callId = callId,
            )
            call.respond(unntakStatistikkList)
        }
    }
}
