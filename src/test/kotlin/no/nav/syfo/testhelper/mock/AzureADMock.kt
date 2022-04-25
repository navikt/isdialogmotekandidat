package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.client.azuread.AzureAdTokenResponse

class AzureADMock {
    private val azureAdTokenResponse = AzureAdTokenResponse(
        access_token = "token",
        expires_in = 3600,
        token_type = "type",
    )
    val name = "azuread"
    val server = embeddedServer(
        factory = Netty,
        port = 0,
    ) {
        installContentNegotiation()
        routing {
            post {
                call.respond(azureAdTokenResponse)
            }
        }
    }

    fun getUrl(): String {
        val port = runBlocking { server.resolvedConnectors().first().port }
        return "http://localhost:$port"
    }
}
