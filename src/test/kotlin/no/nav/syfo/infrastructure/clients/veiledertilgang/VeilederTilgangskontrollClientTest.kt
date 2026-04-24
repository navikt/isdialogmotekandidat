package no.nav.syfo.infrastructure.clients.veiledertilgang

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.azuread.AzureAdToken
import no.nav.syfo.infrastructure.clients.commonConfig
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.mock.respond
import no.nav.syfo.testhelper.testEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class VeilederTilgangskontrollClientTest {
    private val token = "token"
    private val oboToken = "obo-token"
    private val callId = "call-id"
    private val personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val azureAdClient = mockk<AzureAdClient>()

    @BeforeEach
    fun setup() {
        coEvery {
            azureAdClient.getOnBehalfOfToken(any(), any())
        } returns AzureAdToken(
            accessToken = oboToken,
            expires = LocalDateTime.now().plusHours(1),
        )
    }

    @AfterEach
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `hasAccess and hasWriteAccess returns false when tilgang is not approved`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = false, fullTilgang = true))

        runBlocking {
            assertFalse(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess and hasWriteAccess returns true when tilgang is approved and user has fullTilgang`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = true, fullTilgang = true))

        runBlocking {
            assertTrue(client.hasAccess(callId, personident, token))
            assertTrue(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess returns true and hasWriteAccess returns false when tilgang is approved but user does not have fullTilgang`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = true, fullTilgang = false))

        runBlocking {
            assertTrue(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess and hasWriteAccess returns false on unexpected response`() {
        val client = createMockClientForResponse(status = HttpStatusCode.InternalServerError)

        runBlocking {
            assertFalse(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess throws when obo token request fails`() {
        coEvery { azureAdClient.getOnBehalfOfToken(any(), any()) } returns null

        val client = createMockClientForResponse()

        runBlocking {
            assertThrows(RuntimeException::class.java) {
                runBlocking { client.hasAccess(callId, personident, token) }
            }
        }
    }

    private fun createMockClientForResponse(
        tilgang: Tilgang = Tilgang(erGodkjent = true),
        status: HttpStatusCode = HttpStatusCode.OK,
    ): VeilederTilgangskontrollClient {
        val httpClient = HttpClient(MockEngine) {
            commonConfig()
            engine {
                addHandler {
                    if (status == HttpStatusCode.OK) {
                        respond(tilgang, status)
                    } else {
                        respondError(status)
                    }
                }
            }
        }

        return VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = testEnvironment().clients.istilgangskontroll,
            httpClient = httpClient,
        )
    }
}
