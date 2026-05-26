package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.clients.veiledertilgang.Tilgang
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_NO_WRITE_ACCESS
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getNavIdentFromToken

private fun HttpRequestData.navIdent(): String? =
    headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.let { getNavIdentFromToken(it) }

fun MockRequestHandleScope.isTilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) -> {
            val erGodkjent = request.headers[NAV_PERSONIDENT_HEADER] != PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value
            val fullTilgang = request.navIdent() != VEILEDER_IDENT_NO_WRITE_ACCESS
            respond(Tilgang(erGodkjent = erGodkjent, fullTilgang = fullTilgang))
        }
        requestUrl.endsWith(VeilederTilgangskontrollClient.TILGANGSKONTROLL_BRUKERE_PATH) -> {
            val body = runBlocking<List<String>> { request.receiveBody() }.toMutableList()
            body.removeAll { it == PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value }
            respond(body)
        }
        else -> error("Unhandled path $requestUrl")
    }
}
