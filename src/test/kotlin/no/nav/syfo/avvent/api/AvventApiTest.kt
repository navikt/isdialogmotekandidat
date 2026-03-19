package no.nav.syfo.avvent.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.api.AvventDTO
import no.nav.syfo.api.CreateAvventDTO
import no.nav.syfo.api.endpoints.avventApiBasePath
import no.nav.syfo.api.endpoints.avventApiPersonidentPath
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.DialogmotekandidatVurderingRepository
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AvventApiTest {
    private val urlAvventPersonIdent = "$avventApiBasePath/$avventApiPersonidentPath"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val kafkaProducer = mockk<KafkaProducer<String, DialogmotekandidatEndringRecord>>()
    private val dialogmotekandidatEndringProducer =
        DialogmotekandidatEndringProducer(producer = kafkaProducer)

    val dialogmotekandidatVurderingRepository = DialogmotekandidatVurderingRepository(database)

    private val dialogmoteKandidatService = DialogmotekandidatService(
        database = database,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        dialogmotekandidatRepository = externalMockEnvironment.dialogmotekandidatRepository,
        oppfolgingstilfelleService = mockk(relaxed = true),
    )

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            )
        }
        return createClient {
            install(ContentNegotiation) { jackson { configure() } }
        }
    }

    @BeforeEach
    fun before() {
        database.dropData()
        clearMocks(kafkaProducer)
        every { kafkaProducer.send(any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )

    @Test
    fun `creates Avvent when person is kandidat`() = testApplication {
        val client = setupApiAndClient()
        val dialogmotekandidatEndring =
            generateDialogmotekandidatEndringStoppunkt(personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndring)

        val createAvventDTO = CreateAvventDTO(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value,
            frist = LocalDate.now().plusDays(14),
            beskrivelse = "beskrivelse",
        )

        val response = client.post(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createAvventDTO)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }

        val latestDialogmotekandidatEndring =
            dialogmoteKandidatService.getDialogmotekandidatEndringer(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).firstOrNull()
        assertNotNull(latestDialogmotekandidatEndring)
        assertTrue(latestDialogmotekandidatEndring!!.kandidat)

        val getResponse = client.get(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        val avventList = getResponse.body<List<AvventDTO>>()
        assertEquals(1, avventList.size)

        val avventDTO = avventList.first()
        assertEquals(createAvventDTO.personident, avventDTO.personident)
        assertEquals(createAvventDTO.frist, avventDTO.frist)
        assertEquals(createAvventDTO.beskrivelse, avventDTO.beskrivelse)
    }

    @Test
    fun `returns only Avvent records created after latest kandidat status change`() = testApplication {
        val client = setupApiAndClient()

        val firstKandidatEndring =
            generateDialogmotekandidatEndringStoppunkt(personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = firstKandidatEndring)

        val oldAvvent = DialogmotekandidatEndring.avvent(
            frist = LocalDate.now().plusDays(7),
            createdBy = UserConstants.VEILEDER_IDENT,
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
            beskrivelse = "Old avvent",
        )
        database.connection.use { connection ->
            dialogmotekandidatVurderingRepository.createAvvent(connection, oldAvvent)
            connection.commit()
        }

        val ikkeKandidatEndring = DialogmotekandidatEndring.ferdigstiltDialogmote(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = ikkeKandidatEndring)

        val secondKandidatEndring =
            generateDialogmotekandidatEndringStoppunkt(personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = secondKandidatEndring)

        val newAvvent = DialogmotekandidatEndring.avvent(
            frist = LocalDate.now().plusDays(14),
            createdBy = UserConstants.VEILEDER_IDENT,
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
            beskrivelse = "New avvent",
        )
        database.connection.use { connection ->
            externalMockEnvironment.dialogmotekandidatVurderingRepository.createAvvent(connection, newAvvent)
            connection.commit()
        }

        val getResponse = client.get(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }

        val avventList = getResponse.body<List<AvventDTO>>()
        assertEquals(1, avventList.size)
        assertEquals("New avvent", avventList.first().beskrivelse)
    }

    @Test
    fun `returns empty list when person is not currently kandidat`() = testApplication {
        val client = setupApiAndClient()

        val kandidatEndring = generateDialogmotekandidatEndringStoppunkt(personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = kandidatEndring)

        val avvent = DialogmotekandidatEndring.avvent(
            frist = LocalDate.now().plusDays(14),
            createdBy = UserConstants.VEILEDER_IDENT,
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
            beskrivelse = "Test avvent",
        )
        database.connection.use { connection ->
            dialogmotekandidatVurderingRepository.createAvvent(connection, avvent)
            connection.commit()
        }

        val ikkeKandidatEndring = DialogmotekandidatEndring.ferdigstiltDialogmote(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
        )
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = ikkeKandidatEndring)

        val getResponse = client.get(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }

        val avventList = getResponse.body<List<AvventDTO>>()
        assertEquals(0, avventList.size)
    }

    @Test
    fun `returns status Conflict when person is not kandidat`() = testApplication {
        val client = setupApiAndClient()
        val createAvventDTO = CreateAvventDTO(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value,
            frist = LocalDate.now().plusDays(14),
            beskrivelse = "beskrivelse",
        )

        val response = client.post(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createAvventDTO)
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `returns status Unauthorized if no token is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlAvventPersonIdent) {}
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns status Forbidden if denied access to person`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlAvventPersonIdent) { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns status Unauthorized if no token is supplied when creating avvent`() = testApplication {
        val client = setupApiAndClient()
        val createAvventDTO = CreateAvventDTO(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value,
            frist = LocalDate.now().plusDays(14),
            beskrivelse = "beskrivelse",
        )

        val response = client.post(urlAvventPersonIdent) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createAvventDTO)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `returns status Forbidden if denied access to person when creating avvent`() = testApplication {
        val client = setupApiAndClient()
        val createAvventDTO = CreateAvventDTO(
            personident = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value,
            frist = LocalDate.now().plusDays(14),
            beskrivelse = "beskrivelse",
        )

        val response = client.post(urlAvventPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createAvventDTO)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }
}
