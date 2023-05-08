package no.nav.syfo.unntak.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.unntak.api.domain.*
import no.nav.syfo.unntak.database.createUnntak
import no.nav.syfo.unntak.database.getUnntakList
import no.nav.syfo.unntak.domain.UnntakStatistikk
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

class UnntakApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"
    val urlUnntakStatistikk = "$unntakApiBasePath/$unntakApiStatistikk"

    describe(UnntakApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
            val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
                kafkaProducerDialogmotekandidatEndring = kafkaProducer,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            )

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
                                kafkaProducer.send(any())
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
                                kafkaProducer.send(any())
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
                                kafkaProducer.send(any())
                            }
                        }
                    }
                }
            }
            describe("Get unntaksstatistikk for veileder") {
                describe("Happy path") {
                    it("returns unntaksstatistikk if request is successful") {
                        val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
                        database.connection.use {
                            it.createUnntak(unntak)
                            it.commit()
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlUnntakStatistikk) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val unntakStatistikkList =
                                objectMapper.readValue<List<UnntakStatistikk>>(response.content!!)
                            unntakStatistikkList.size shouldBeEqualTo 1

                            val unntakStatistikk = unntakStatistikkList.first()
                            unntakStatistikk.unntakDato shouldNotBeEqualTo null
                            unntakStatistikk.tilfelleStart shouldNotBeEqualTo null
                            unntakStatistikk.tilfelleEnd shouldNotBeEqualTo null
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
                    it("returns empty unntaksstatistikk if no access to person with unntak") {
                        val unntak =
                            generateNewUnntakDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS).toUnntak(
                                createdByIdent = UserConstants.VEILEDER_IDENT
                            )
                        database.connection.use {
                            it.createUnntak(unntak)
                            it.commit()
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlUnntakStatistikk) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val unntakStatistikkList =
                                objectMapper.readValue<List<UnntakStatistikk>>(response.content!!)
                            unntakStatistikkList.size shouldBeEqualTo 0
                        }
                    }
                }
            }
        }
    }
})
