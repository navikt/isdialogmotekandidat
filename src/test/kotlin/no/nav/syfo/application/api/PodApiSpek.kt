import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerPodApi
import no.nav.syfo.testhelper.TestDatabase
import no.nav.syfo.testhelper.TestDatabaseNotResponding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    describe("Successful liveness and readiness checks") {
        testApplication {
            val database = TestDatabase()

            application {
                routing {
                    registerPodApi(
                        applicationState = ApplicationState(
                            alive = true,
                            ready = true
                        ),
                        database = database,
                    )
                }
            }

            it("Returns ok on is_alive") {
                runTest {
                    val response = client.get("/internal/is_alive")
                    response.status.isSuccess() shouldBeEqualTo true
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_ready") {
                runTest {
                    val response = client.get("/internal/is_ready")
                    response.status.isSuccess() shouldBeEqualTo true
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        testApplication {
            val database = TestDatabase()

            application {
                routing {
                    registerPodApi(
                        applicationState = ApplicationState(
                            alive = false,
                            ready = false,
                        ),
                        database = database,
                    )
                }
            }

            it("Returns internal server error when liveness check fails") {
                runTest {
                    val response = client.get("/internal/is_alive")
                    response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
            it("Returns internal server error when readiness check fails") {
                runTest {
                    val response = client.get("/internal/is_ready")
                    response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
        }
    }
    describe("Successful liveness and unsuccessful readiness checks when database not working") {
        testApplication {
            val database = TestDatabaseNotResponding()

            application {
                routing {
                    registerPodApi(
                        applicationState = ApplicationState(
                            alive = true,
                            ready = true,
                        ),
                        database = database,
                    )
                }
            }

            it("Returns internal server error when liveness check fails") {
                runTest {
                    val response = client.get("/internal/is_alive")
                    response.status.isSuccess() shouldBeEqualTo true
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
            it("Returns internal server error when readiness check fails") {
                runTest {
                    val response = client.get("/internal/is_ready")
                    response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.bodyAsText() shouldNotBeEqualTo null
                }
            }
        }
    }
})
