package no.nav.syfo.client.oppfolgingstilfelle

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.util.UUID

class OppfolgingstilfelleClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val personOppfolgingstilfelleSystemUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH"
    private val personOppfolgingstilfelleVeilederUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH"
    private val personsOppfolgingstilfelleVeilederUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSONS_PATH"

    suspend fun getOppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
        veilederToken: String? = null,
        callId: String?,
    ): OppfolgingstilfellePersonDTO? {
        val callIdToUse = callId ?: UUID.randomUUID().toString()
        return try {
            val token = getAzureToken(veilederToken)
                ?: throw RuntimeException("Could not get azuread access token")
            val path = getPath(veilederToken)
            val response: HttpResponse = httpClient.get(path) {
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header(NAV_CALL_ID_HEADER, callIdToUse)
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                accept(ContentType.Application.Json)
            }
            response.body<OppfolgingstilfellePersonDTO>().also {
                COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS.increment()
            }
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting OppfolgingstilfellePerson from Isoppfolgingstilfelle with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                callIdArgument(callIdToUse),
            )
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL.increment()
            throw responseException
        }
    }

    suspend fun getOppfolgingstilfellePersons(
        personIdents: List<PersonIdentNumber>,
        veilederToken: String,
        callId: String,
    ): List<OppfolgingstilfellePersonDTO> {
        return try {
            val token = azureAdClient.getOnBehalfOfToken(clientEnvironment.clientId, veilederToken)
                ?: throw RuntimeException("Could not get azuread access token")
            val response: HttpResponse = httpClient.post(personsOppfolgingstilfelleVeilederUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(personIdents.map { it.value })
            }
            response.body<List<OppfolgingstilfellePersonDTO>>().also {
                COUNT_CALL_OPPFOLGINGSTILFELLE_PERSONS_SUCCESS.increment()
            }
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting OppfolgingstilfellePerson for persons from Isoppfolgingstilfelle with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                callIdArgument(callId),
            )
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSONS_FAIL.increment()
            throw responseException
        }
    }

    private suspend fun getAzureToken(token: String?) =
        if (token == null)
            azureAdClient.getSystemToken(clientEnvironment.clientId)
        else
            azureAdClient.getOnBehalfOfToken(clientEnvironment.clientId, token)

    private fun getPath(token: String?) =
        if (token == null)
            personOppfolgingstilfelleSystemUrl
        else
            personOppfolgingstilfelleVeilederUrl

    companion object {
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH =
            "/api/system/v1/oppfolgingstilfelle/personident"

        private const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_COMMON_PATH =
            "/api/internad/v1/oppfolgingstilfelle"

        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH =
            "$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_COMMON_PATH/personident"
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSONS_PATH =
            "$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_COMMON_PATH/persons"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
    }
}
