package no.nav.syfo.infrastructure.clients.pdl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.httpClientDefault
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.pdl.domain.IdentType
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlHentIdenter
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlHentIdenterRequest
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlHentIdenterRequestVariables
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlIdenterResponse
import no.nav.syfo.infrastructure.clients.pdl.domain.errorMessage
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val pdlEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getPdlIdenter(
        personIdent: Personident,
        callId: String? = null,
    ): PdlHentIdenter? {
        val token = azureAdClient.getSystemToken(pdlEnvironment.clientId)
            ?: throw RuntimeException("Failed to send PdlHentIdenterRequest to PDL: No token was found")

        val query = getPdlQuery(
            queryFilePath = "/pdl/hentIdenter.graphql",
        )

        val request = PdlHentIdenterRequest(
            query = query,
            variables = PdlHentIdenterRequestVariables(
                ident = personIdent.value,
                historikk = true,
                grupper = listOf(
                    IdentType.FOLKEREGISTERIDENT,
                ),
            ),
        )

        val response: HttpResponse = httpClient.post(pdlEnvironment.baseUrl) {
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
            header(NAV_CALL_ID_HEADER, callId)
            header(IDENTER_HEADER, IDENTER_HEADER)
            setBody(request)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlIdenterResponse = response.body<PdlIdenterResponse>()
                return if (!pdlIdenterResponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_IDENTER_FAIL.increment()
                    pdlIdenterResponse.errors.forEach {
                        logger.error("Error while requesting IdentList from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
                    pdlIdenterResponse.data
                }
            }
            else -> {
                COUNT_CALL_PDL_IDENTER_FAIL.increment()
                logger.error("Request to get IdentList with url: ${pdlEnvironment.clientId} failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    private fun getPdlQuery(queryFilePath: String): String {
        return this::class.java.getResource(queryFilePath)!!
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        const val IDENTER_HEADER = "identer"

        // Se behandlingskatalog https://behandlingskatalog.intern.nav.no/process/purpose/SYFO/60868e2d-b151-47a3-a1a6-1d896fc3a88d
        private const val BEHANDLINGSNUMMER_HEADER_KEY = "behandlingsnummer"
        private const val BEHANDLINGSNUMMER_HEADER_VALUE = "B426"
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}
