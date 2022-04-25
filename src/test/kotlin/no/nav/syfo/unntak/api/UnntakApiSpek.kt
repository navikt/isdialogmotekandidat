package no.nav.syfo.unntak.api

import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UnntakApiSpek : Spek({
    val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"

    describe(UnntakApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            describe("Create unntak for person") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {
                    it("creates unntak and DialogmotekandidatEndring (not kandidat) for person") {
                        with(
                            handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                    }
                }
                describe("Unhappy paths") {
                    it("returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }
            }
        }
    }
})
