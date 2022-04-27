package no.nav.syfo.unntak.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UnntakApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"

    describe("${UnntakApiSpek::class.java.simpleName}: Create unntak for person") {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            )

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            )

            beforeEachTest {
                database.dropData()

                clearMocks(dialogmotekandidatEndringProducer)
                justRun { dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any()) }
            }

            describe("Happy path") {
                it("creates Unntak and DialogmotekandidatEndring (not kandidat) for person") {
                    val newUnntakDTO =
                        generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
                    with(
                        handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newUnntakDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Created
                        verify(exactly = 1) {
                            dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                        }

                        val latestDialogmotekandidatEndring =
                            database.connection.getDialogmotekandidatEndringListForPerson(
                                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
                            ).firstOrNull()
                        latestDialogmotekandidatEndring shouldNotBeEqualTo null
                        latestDialogmotekandidatEndring!!.kandidat shouldBeEqualTo false
                        latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.UNNTAK.name

                        // TODO: Test unntak persisted
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status Unauthorized if no token is supplied") {
                    with(
                        handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {}
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) {
                            dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                        }
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    val newUnntakDTO =
                        generateNewUnntakDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
                    with(
                        handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newUnntakDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        verify(exactly = 0) {
                            dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                        }
                    }
                }
            }
        }
    }
})
