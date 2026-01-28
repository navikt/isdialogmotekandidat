package no.nav.syfo.ikkeaktuell.api

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
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.api.CreateIkkeAktuellDTO
import no.nav.syfo.api.endpoints.ikkeAktuellApiBasePath
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.IkkeAktuellArsak
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.isIkkeKandidat
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class IkkeAktuellApiTest {
    private val urlIkkeAktuellPersonIdent = "$ikkeAktuellApiBasePath/personident"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val repository = externalMockEnvironment.dialogmotekandidatVurderingRepository
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val endringProducer = DialogmotekandidatEndringProducer(kafkaProducerDialogmotekandidatEndring = kafkaProducer)

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(externalMockEnvironment, dialogmotekandidatEndringProducer = endringProducer)
        }
        return createClient { install(ContentNegotiation) { jackson { configure() } } }
    }

    @BeforeEach
    fun setup() {
        database.dropData()
        clearMocks(kafkaProducer)
        every { kafkaProducer.send(any()) } returns mockk(relaxed = true)
    }

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val newIkkeAktuellDTO = generateNewIkkeAktuellDTO(personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)

    @Test
    fun `creates IkkeAktuell and DialogmotekandidatEndring (not kandidat) when person is kandidat`() = testApplication {
        val client = setupApiAndClient()
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(stoppunktEndring)
        assertFalse(database.isIkkeKandidat(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))

        val response = client.post(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(newIkkeAktuellDTO)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }
        assertTrue(database.isIkkeKandidat(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))
        val latestEndring = database.connection.use { it.getDialogmotekandidatEndringListForPerson(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).firstOrNull() }
        assertNotNull(latestEndring)
        assertFalse(latestEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.IKKE_AKTUELL.name, latestEndring.arsak)
        val kafkaValue = producerRecordSlot.captured.value()
        assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaValue.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.IKKE_AKTUELL.name, kafkaValue.arsak)
        assertFalse(kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
        assertNull(kafkaValue.unntakVeilederident)
    }

    @Test
    fun `returns status Unauthorized if no token is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.post(urlIkkeAktuellPersonIdent) {}
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `returns status Forbidden if denied access to person`() = testApplication {
        val client = setupApiAndClient()
        val dtoDenied = generateNewIkkeAktuellDTO(personident = UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
        val response = client.post(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(dtoDenied)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `returns Conflict when person is not kandidat`() = testApplication {
        val client = setupApiAndClient()
        val response = client.post(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(newIkkeAktuellDTO)
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    private fun newIkkeAktuellVurdering() = IkkeAktuell(
        uuid = UUID.randomUUID(),
        createdAt = LocalDateTime.now().atOffset(ZoneOffset.UTC),
        createdBy = UserConstants.VEILEDER_IDENT,
        personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
        arsak = IkkeAktuellArsak.ARBEIDSTAKER_AAP,
        beskrivelse = "Dette er en beskrivelse for hvorfor personen ikke er aktuell for dialogmøte",
    )

    @Test
    fun `Successfully retrieves ikke aktuell vurderinger for person`() = testApplication {
        database.connection.use { connection ->
            repository.createIkkeAktuell(connection, true, newIkkeAktuellVurdering())
            repository.createIkkeAktuell(connection, true, newIkkeAktuellVurdering())
        }
        val client = setupApiAndClient()
        val response = client.get(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<IkkeAktuell>>()
        assertEquals(2, body.size)
    }

    @Test
    fun `Fails to retrieves ikke aktuell vurderinger for person when another person has vurdering`() = testApplication {
        database.connection.use { connection ->
            repository.createIkkeAktuell(connection, true, newIkkeAktuellVurdering())
        }
        val client = setupApiAndClient()
        val response = client.get(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_ALTERNATIVE.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<IkkeAktuell>>()
        assertEquals(0, body.size)
    }

    @Test
    fun `returns status Forbidden if denied access to person on get vurderinger`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlIkkeAktuellPersonIdent) {
            bearerAuth(validToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}

fun generateNewIkkeAktuellDTO(personident: Personident) = CreateIkkeAktuellDTO(
    personIdent = personident.value,
    arsak = IkkeAktuellArsak.DIALOGMOTE_AVHOLDT.name,
    beskrivelse = "Dette er en beskrivelse",
)
