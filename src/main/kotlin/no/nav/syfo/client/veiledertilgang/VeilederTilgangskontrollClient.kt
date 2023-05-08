package no.nav.syfo.client.veiledertilgang

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
) {
    private val httpClient = httpClientDefault()
    private val tilgangskontrollPersonUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_PERSON_PATH"
    private val tilgangskontrollPersonListUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_PERSON_LIST_PATH"

    suspend fun hasAccess(
        callId: String,
        personIdent: PersonIdentNumber,
        token: String,
    ): Boolean {
        val onBehalfOfToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientEnvironment.clientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        return try {
            val tilgang = httpClient.get(tilgangskontrollPersonUrl) {
                header(HttpHeaders.Authorization, bearerHeader(onBehalfOfToken))
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            tilgang.body<Tilgang>().harTilgang
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(response = e.response, resource = resourcePerson, callId = callId)
            }
            false
        }
    }

    suspend fun hasAccessToPersonList(
        personIdentList: List<PersonIdentNumber>,
        token: String,
        callId: String,
    ): List<PersonIdentNumber> {
        val onBehalfOfToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientEnvironment.clientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to PersonList: Failed to get OBO token")

        return try {
            val response: HttpResponse = httpClient.post(tilgangskontrollPersonListUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token = onBehalfOfToken))
                header(NAV_CALL_ID_HEADER, value = callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(personIdentList.map { it.value })
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS.increment()
            response.body<List<String>>().map { personIdent -> PersonIdentNumber(personIdent) }
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(response = e.response, resource = resourcePersonList, callId = callId)
            }
            emptyList()
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        resource: String,
        callId: String,
    ) {
        log.error(
            "Error while requesting access to $resource from syfo-tilgangskontroll with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        incrementFailCounter(resource)
    }

    private fun incrementFailCounter(resource: String) {
        when (resource) {
            resourcePerson -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.increment()
            }

            resourcePersonList -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL.increment()
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        private const val resourcePerson = "PERSON"
        private const val resourcePersonList = "PERSONLIST"

        private const val TILGANGSKONTROLL_COMMON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident"
        const val TILGANGSKONTROLL_PERSON_PATH = "$TILGANGSKONTROLL_COMMON_PATH/person"
        const val TILGANGSKONTROLL_PERSON_LIST_PATH = "$TILGANGSKONTROLL_COMMON_PATH/brukere"
    }
}
