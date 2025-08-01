package no.nav.syfo.unntak.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.endpoints.unntakApiBasePath
import no.nav.syfo.api.endpoints.unntakApiPersonidentPath
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.api.UnntakDTO
import no.nav.syfo.api.toUnntak
import no.nav.syfo.infrastructure.database.createUnntak
import no.nav.syfo.infrastructure.database.getUnntakList
import no.nav.syfo.domain.UnntakArsak
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

class UnntakApiSpek : Spek({
    val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"

    describe(UnntakApiSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
        val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
            kafkaProducerDialogmotekandidatEndring = kafkaProducer,
        )

        fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
            application {
                testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
                )
            }
            val client = createClient {
                install(ContentNegotiation) {
                    jackson { configure() }
                }
            }
            return client
        }

        beforeEachTest {
            database.dropData()

            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
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
                    testApplication {
                        val client = setupApiAndClient()

                        val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
                        database.connection.use {
                            it.createUnntak(unntak)
                            it.commit()
                        }

                        val response = client.get(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val unntakList = response.body<List<UnntakDTO>>()
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
                it("returns unntak with arsak FRISKMELDT for person") {
                    testApplication {
                        val client = setupApiAndClient()

                        val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT).copy(
                            arsak = UnntakArsak.FRISKMELDT
                        )
                        database.connection.use {
                            it.createUnntak(unntak)
                            it.commit()
                        }

                        val response = client.get(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val unntakList = response.body<List<UnntakDTO>>()
                        unntakList.size shouldBeEqualTo 1

                        val unntakDTO = unntakList.first()
                        unntakDTO.arsak shouldBeEqualTo UnntakArsak.FRISKMELDT.name
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlUnntakPersonIdent) {}
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()

                        val response = client.get(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
                it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient()

                        val response = client.get(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient()

                        val response = client.get(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }

        describe("Create unntak for person") {
            describe("Happy path") {
                it("creates Unntak and DialogmotekandidatEndring (not kandidat) when person is kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)
                        val response = client.post(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newUnntakDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Created
                        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
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

                        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
                        kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                        kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.UNNTAK.name
                        kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo false
                        kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo newUnntakDTO.arsak
                        kafkaDialogmoteKandidatEndring.unntakVeilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status BadRequest when arsak is FRISKMELDT or ARBEIDSFORHOLD_OPPHORT") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)

                        client.post(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(
                                generateNewUnntakDTO(
                                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                                    arsak = UnntakArsak.FRISKMELDT
                                )
                            )
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.BadRequest
                        }

                        client.post(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(
                                generateNewUnntakDTO(
                                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                                    arsak = UnntakArsak.ARBEIDSFORHOLD_OPPHORT
                                )
                            )
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
                it("returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(urlUnntakPersonIdent) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newUnntakDTO)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) {
                            kafkaProducer.send(any())
                        }
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val newUnntakDTOWithDeniedAccess =
                            generateNewUnntakDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
                        val response = client.post(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newUnntakDTOWithDeniedAccess)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                        verify(exactly = 0) {
                            kafkaProducer.send(any())
                        }
                    }
                }
                it("returns Conflict when person is not kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(urlUnntakPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newUnntakDTO)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Conflict
                        verify(exactly = 0) {
                            kafkaProducer.send(any())
                        }
                    }
                }
            }
        }
    }
})
