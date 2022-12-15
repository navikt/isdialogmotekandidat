package no.nav.syfo.client.oppfolgingstilfelle

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
    private val clientEnvironment: ClientEnvironment
) {
    private val personOppfolgingstilfelleSystemUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH"
    private val personOppfolgingstilfelleVeilederUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH"

    private val httpClient = httpClientDefault()

    suspend fun getOppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
        veilederToken: String? = null,
        callId: String?,
    ): OppfolgingstilfellePersonDTO? {
        val callIdToUse = if (callId != null) callId else UUID.randomUUID().toString()
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

        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH =
            "/api/internad/v1/oppfolgingstilfelle/personident"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
    }
}
