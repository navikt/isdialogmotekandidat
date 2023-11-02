package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_PERSON_LIST_PATH
import no.nav.syfo.testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER

suspend fun MockRequestHandleScope.isTilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    return if (requestUrl.endsWith(TILGANGSKONTROLL_PERSON_LIST_PATH)) {
        val personidenter = request.receiveBody<List<String>>()
        val personIdenterWithAccess = personidenter
            .filter { it != PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value }
        respond(personIdenterWithAccess)
    } else {
        when (request.headers[NAV_PERSONIDENT_HEADER]) {
            PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value -> respond(Tilgang(erGodkjent = false))
            else -> respond(Tilgang(erGodkjent = true))
        }
    }
}
