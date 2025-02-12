package no.nav.syfo.ikkeaktuell.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.ikkeaktuell.api.domain.CreateIkkeAktuellDTO
import no.nav.syfo.ikkeaktuell.domain.IkkeAktuellArsak
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.util.configure

import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

class IkkeAktuellApiSpek : Spek({
    val urlIkkeAktuellPersonIdent = "$ikkeAktuellApiBasePath/$ikkeAktuellApiPersonidentPath"

    describe(IkkeAktuellApiSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
        val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
            kafkaProducerDialogmotekandidatEndring = kafkaProducer,
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
        val newIkkeAktuellDTO = generateNewIkkeAktuellDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)

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

        describe("Create ikke aktuell for person") {
            describe("Happy path") {
                it("creates IkkeAktuell and DialogmotekandidatEndring (not kandidat) when person is kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)
                        database.isIkkeKandidat(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER) shouldBe false

                        val response = client.post(urlIkkeAktuellPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newIkkeAktuellDTO)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Created
                        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        database.isIkkeKandidat(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER) shouldBe true

                        val latestDialogmotekandidatEndring =
                            database.connection.getDialogmotekandidatEndringListForPerson(
                                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
                            ).firstOrNull()
                        latestDialogmotekandidatEndring shouldNotBeEqualTo null
                        latestDialogmotekandidatEndring!!.kandidat shouldBeEqualTo false
                        latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.IKKE_AKTUELL.name

                        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
                        kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                        kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.IKKE_AKTUELL.name
                        kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo false
                        kafkaDialogmoteKandidatEndring.unntakArsak shouldBe null
                        kafkaDialogmoteKandidatEndring.veilederident shouldBe null
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()

                        val response = client.post(urlIkkeAktuellPersonIdent) {}
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) {
                            kafkaProducer.send(any())
                        }
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val newIkkeAktuellDTOWithDeniedAccess =
                            generateNewIkkeAktuellDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)

                        val response = client.post(urlIkkeAktuellPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newIkkeAktuellDTOWithDeniedAccess)
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
                        val response = client.post(urlIkkeAktuellPersonIdent) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newIkkeAktuellDTO)
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

fun generateNewIkkeAktuellDTO(
    personIdent: PersonIdentNumber,
) = CreateIkkeAktuellDTO(
    personIdent = personIdent.value,
    arsak = IkkeAktuellArsak.DIALOGMOTE_AVHOLDT.name,
    beskrivelse = "Dette er en beskrivelse",
)
