package no.nav.syfo.dialogmotekandidat.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.api.endpoints.kandidatApiBasePath
import no.nav.syfo.api.endpoints.kandidatApiPersonidentPath
import no.nav.syfo.domain.Dialogmotekandidat
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import no.nav.syfo.util.toLocalDateTimeOslo
import no.nav.syfo.util.toOffsetDatetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DialogmotekandidatApiTest {
    private val urlKandidatPersonIdent = "$kandidatApiBasePath/$kandidatApiPersonidentPath"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val endringProducer = mockk<DialogmotekandidatEndringProducer>()

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(externalMockEnvironment, dialogmotekandidatEndringProducer = endringProducer)
        }
        return createClient { install(ContentNegotiation) { jackson { configure() } } }
    }

    @BeforeEach
    fun setup() { database.dropData() }

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )

    @Test
    fun `returns kandidat=false for person that has never been Kandidat`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertFalse(kandidat.kandidat)
        assertNull(kandidat.kandidatAt)
    }

    @Test
    fun `returns kandidat=false for person generated as Stoppunkt-Kandidat today`() = testApplication {
        val client = setupApiAndClient()
        val endring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(endring)
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertFalse(kandidat.kandidat)
        assertNull(kandidat.kandidatAt)
    }

    @Test
    fun `returns kandidat=false for person generated as Stoppunkt-Kandidat 6 days ago`() = testApplication {
        val client = setupApiAndClient()
        val endring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).copy(createdAt = LocalDate.now().minusDays(6).toOffsetDatetime())
        database.createDialogmotekandidatEndring(endring)
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertFalse(kandidat.kandidat)
        assertNull(kandidat.kandidatAt)
    }

    @Test
    fun `returns kandidat=true for person generated as Stoppunkt-Kandidat 7 days ago`() = testApplication {
        val client = setupApiAndClient()
        val endring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).copy(createdAt = LocalDate.now().minusDays(7).toOffsetDatetime())
        database.createDialogmotekandidatEndring(endring)
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertTrue(kandidat.kandidat)
        assertEquals(
            endring.createdAt.toLocalDateTimeOslo().toInstant(ZoneOffset.UTC).toEpochMilli(),
            kandidat.kandidatAt?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        )
    }

    @Test
    fun `returns kandidat=false for person Stoppunkt-Kandidat not in current oppfolgingstilfelle`() = testApplication {
        val client = setupApiAndClient()
        val endring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).copy(createdAt = OffsetDateTime.now().minusYears(1))
        database.createDialogmotekandidatEndring(endring)
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertFalse(kandidat.kandidat)
    }

    @Test
    fun `returns kandidat=false for person previously Kandidat`() = testApplication {
        val client = setupApiAndClient()
        val stoppunkt = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(stoppunkt)
        val ferdigstilt = generateDialogmotekandidatEndringFerdigstilt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.createDialogmotekandidatEndring(ferdigstilt)
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val kandidat = response.body<Dialogmotekandidat>()
        assertFalse(kandidat.kandidat)
        assertNull(kandidat.kandidatAt)
    }

    @Test
    fun `returns status Unauthorized if no token is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlKandidatPersonIdent) {}
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns status Forbidden if denied access to person`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlKandidatPersonIdent) { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(urlKandidatPersonIdent) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
