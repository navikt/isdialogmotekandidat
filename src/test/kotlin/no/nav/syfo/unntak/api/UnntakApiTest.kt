package no.nav.syfo.unntak.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.UnntakDTO
import no.nav.syfo.api.endpoints.unntakApiBasePath
import no.nav.syfo.api.endpoints.unntakApiPersonidentPath
import no.nav.syfo.api.toUnntak
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.UnntakArsak
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Future

class UnntakApiTest {
    private val urlUnntakPersonIdent = "$unntakApiBasePath/$unntakApiPersonidentPath"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmotekandidatVurderingRepository = externalMockEnvironment.dialogmotekandidatVurderingRepository
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
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

    @BeforeEach
    fun setup() {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val newUnntakDTO = generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)

    @Test
    fun `returns unntak for person if request is successful`() {
        testApplication {
            val client = setupApiAndClient()

            val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
            database.connection.use {
                dialogmotekandidatVurderingRepository.createUnntak(it, unntak)
                it.commit()
            }

            val response = client.get(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val unntakList = response.body<List<UnntakDTO>>()
            assertEquals(1, unntakList.size)

            val unntakDTO = unntakList.first()
            assertNotNull(unntakDTO.createdAt)
            assertNotNull(unntakDTO.uuid)
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value, unntakDTO.personIdent)
            assertEquals(UserConstants.VEILEDER_IDENT, unntakDTO.createdBy)
            assertEquals(newUnntakDTO.arsak, unntakDTO.arsak)
            assertEquals(newUnntakDTO.beskrivelse, unntakDTO.beskrivelse)
        }
    }

    @Test
    fun `returns unntak with arsak FRISKMELDT for person`() {
        testApplication {
            val client = setupApiAndClient()

            val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT).copy(
                arsak = UnntakArsak.FRISKMELDT
            )
            database.connection.use {
                dialogmotekandidatVurderingRepository.createUnntak(it, unntak)
                it.commit()
            }

            val response = client.get(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val unntakList = response.body<List<UnntakDTO>>()
            assertEquals(1, unntakList.size)

            val unntakDTO = unntakList.first()
            assertEquals(UnntakArsak.FRISKMELDT.name, unntakDTO.arsak)
        }
    }

    @Test
    fun `returns status Unauthorized if no token is supplied when getting unntak`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlUnntakPersonIdent) {}
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `returns status Forbidden if denied access to person when getting unntak`() {
        testApplication {
            val client = setupApiAndClient()

            val response = client.get(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
        testApplication {
            val client = setupApiAndClient()

            val response = client.get(urlUnntakPersonIdent) {
                bearerAuth(validToken)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
        testApplication {
            val client = setupApiAndClient()

            val response = client.get(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `creates Unntak and DialogmotekandidatEndring (not kandidat) when person is kandidat`() {
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

            assertEquals(HttpStatusCode.Created, response.status)
            val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
            verify(exactly = 1) {
                kafkaProducer.send(capture(producerRecordSlot))
            }

            val latestDialogmotekandidatEndring =
                database.connection.getDialogmotekandidatEndringListForPerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
                ).firstOrNull()
            assertNotNull(latestDialogmotekandidatEndring)
            assertEquals(false, latestDialogmotekandidatEndring!!.kandidat)
            assertEquals(DialogmotekandidatEndringArsak.UNNTAK.name, latestDialogmotekandidatEndring.arsak)

            val latestUnntak =
                dialogmotekandidatVurderingRepository.getUnntakList(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
                ).first()
            assertNotNull(latestUnntak.createdAt)
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value, latestUnntak.personIdent)
            assertEquals(UserConstants.VEILEDER_IDENT, latestUnntak.createdBy)
            assertEquals(newUnntakDTO.arsak, latestUnntak.arsak)
            assertEquals(newUnntakDTO.beskrivelse, latestUnntak.beskrivelse)

            val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
            assertEquals(DialogmotekandidatEndringArsak.UNNTAK.name, kafkaDialogmoteKandidatEndring.arsak)
            assertEquals(false, kafkaDialogmoteKandidatEndring.kandidat)
            assertEquals(newUnntakDTO.arsak, kafkaDialogmoteKandidatEndring.unntakArsak)
            assertEquals(UserConstants.VEILEDER_IDENT, kafkaDialogmoteKandidatEndring.unntakVeilederident)
        }
    }

    @Test
    fun `returns status BadRequest when arsak is FRISKMELDT or ARBEIDSFORHOLD_OPPHORT`() {
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
                assertEquals(HttpStatusCode.BadRequest, status)
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
                assertEquals(HttpStatusCode.BadRequest, status)
            }
        }
    }

    @Test
    fun `returns status Unauthorized if no token is supplied when creating unntak`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.post(urlUnntakPersonIdent) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(newUnntakDTO)
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }
        }
    }

    @Test
    fun `returns status Forbidden if denied access to person when creating unntak`() {
        testApplication {
            val client = setupApiAndClient()
            val newUnntakDTOWithDeniedAccess =
                generateNewUnntakDTO(personIdent = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
            val response = client.post(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(newUnntakDTOWithDeniedAccess)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }
        }
    }

    @Test
    fun `returns Conflict when person is not kandidat`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.post(urlUnntakPersonIdent) {
                bearerAuth(validToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(newUnntakDTO)
            }
            assertEquals(HttpStatusCode.Conflict, response.status)
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }
        }
    }
}
