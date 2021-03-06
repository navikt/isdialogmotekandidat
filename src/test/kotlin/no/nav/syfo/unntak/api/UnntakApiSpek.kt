package no.nav.syfo.unntak.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.unntak.api.domain.UnntakDTO
import no.nav.syfo.unntak.api.domain.toUnntak
import no.nav.syfo.unntak.database.createUnntak
import no.nav.syfo.unntak.database.getUnntakList
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UnntakApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"

    describe(UnntakApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            )

            beforeEachTest {
                database.dropData()

                clearMocks(dialogmotekandidatEndringProducer)
                justRun { dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any()) }
            }

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
            val newUnntakDTO = generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)

            describe("Get unntak for person") {
                describe("Happy path") {
                    it("returns unntak for person if request is successful") {
                        val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
                        database.connection.use {
                            it.createUnntak(unntak)
                            it.commit()
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val unntakList = objectMapper.readValue<List<UnntakDTO>>(response.content!!)
                            unntakList.size shouldBeEqualTo 1

                            val unntakDTO = unntakList.first()
                            unntakDTO.createdAt shouldNotBeEqualTo null
                            unntakDTO.uuid shouldNotBeEqualTo null
                            unntakDTO.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                            unntakDTO.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            unntakDTO.arsak shouldBeEqualTo newUnntakDTO.arsak
                            unntakDTO.beskrivelse shouldBeEqualTo newUnntakDTO.beskrivelse
                        }
                    }
                }
                describe("Unhappy paths") {
                    it("returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlUnntakPersonIdent) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Get, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1)
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }

            describe("Create unntak for person") {
                describe("Happy path") {
                    it("creates Unntak and DialogmotekandidatEndring (not kandidat) when person is kandidat") {
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)
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

                            val latestUnntak =
                                database.getUnntakList(
                                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
                                ).first()
                            latestUnntak.createdAt shouldNotBeEqualTo null
                            latestUnntak.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                            latestUnntak.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            latestUnntak.arsak shouldBeEqualTo newUnntakDTO.arsak
                            latestUnntak.beskrivelse shouldBeEqualTo newUnntakDTO.beskrivelse
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
                        val newUnntakDTOWithDeniedAccess =
                            generateNewUnntakDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
                        with(
                            handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newUnntakDTOWithDeniedAccess))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) {
                                dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                            }
                        }
                    }
                    it("returns Conflict when person is not kandidat") {
                        with(
                            handleRequest(HttpMethod.Post, urlUnntakPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newUnntakDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                            verify(exactly = 0) {
                                dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any())
                            }
                        }
                    }
                }
            }
        }
    }
})
