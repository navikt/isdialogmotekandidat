package no.nav.syfo.application.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.api.registerPodApi
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.TestDatabaseNotResponding
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodApiTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val databaseNotResponding = TestDatabaseNotResponding()

    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Test
    fun `Returns ok on is_alive`() {
        testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true),
            )
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Test
    fun `Returns ok on is_ready`() {
        testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true),
            )
            val response = client.get("/internal/is_ready")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Test
    fun `Returns internal server error when liveness check fails`() {
        testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = false, ready = false),
            )
            val response = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }

    @Test
    fun `Returns internal server error when readiness check fails`() {
        testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = false),
            )
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }

    @Test
    fun `Returns ok on is_alive when database not working`() {
        testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = false),
            )
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Test
    fun `Returns internal server error on is_ready when database not working`() {
        testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = true),
            )
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }
}
