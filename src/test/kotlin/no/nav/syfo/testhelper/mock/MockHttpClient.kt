package no.nav.syfo.testhelper.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.application.Environment
import no.nav.syfo.client.commonConfig
import no.nav.syfo.util.configuredJacksonMapper

val mapper = configuredJacksonMapper()

fun <T> MockRequestHandleScope.respond(body: T): HttpResponseData =
    respond(
        mapper.writeValueAsString(body),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
    )

suspend inline fun <reified T> HttpRequestData.receiveBody(): T {
    return mapper.readValue(body.toByteArray(), T::class.java)
}

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azure.openidConfigTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.clients.syfotilgangskontroll.baseUrl}") -> syfoTilgangskontrollResponse(
                    request
                )

                requestUrl.startsWith("/${environment.clients.pdl.baseUrl}") -> pdlMockResponse(request)
                requestUrl.startsWith("/${environment.clients.oppfolgingstilfelle.baseUrl}") -> oppfolgingstilfelleMockResponse(request)
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
