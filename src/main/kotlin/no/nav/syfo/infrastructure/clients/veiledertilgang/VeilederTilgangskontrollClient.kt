package no.nav.syfo.infrastructure.clients.veiledertilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.httpClientDefault
import no.nav.syfo.domain.Personident
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val tilgangskontrollPersonUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_PERSON_PATH"

    suspend fun hasAccess(
        callId: String,
        personident: Personident,
        token: String,
    ): Boolean {
        val onBehalfOfToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientEnvironment.clientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        return try {
            val tilgang = httpClient.get(tilgangskontrollPersonUrl) {
                header(HttpHeaders.Authorization, bearerHeader(onBehalfOfToken))
                header(NAV_PERSONIDENT_HEADER, personident.value)
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            tilgang.body<Tilgang>().erGodkjent
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
            } else {
                log.error(
                    "Error while requesting access to person from istilgangskontroll with {}, {}",
                    StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                    callIdArgument(callId = callId)
                )
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.increment()
            }
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        private const val TILGANGSKONTROLL_COMMON_PATH = "/api/tilgang/navident"
        const val TILGANGSKONTROLL_PERSON_PATH = "$TILGANGSKONTROLL_COMMON_PATH/person"
    }
}
