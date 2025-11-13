package no.nav.syfo.dialogmotekandidat.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.HistorikkDTO
import no.nav.syfo.api.HistorikkType
import no.nav.syfo.api.endpoints.kandidatApiBasePath
import no.nav.syfo.api.endpoints.kandidatApiHistorikkPath
import no.nav.syfo.api.toIkkeAktuell
import no.nav.syfo.api.toUnntak
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Unntak
import no.nav.syfo.ikkeaktuell.api.generateNewIkkeAktuellDTO
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatEndring
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import no.nav.syfo.util.nowUTC
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DialogmotekandidatHistorikkApiTest {
    private val historikkUrl = "$kandidatApiBasePath/$kandidatApiHistorikkPath"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vurderingRepository = externalMockEnvironment.dialogmotekandidatVurderingRepository

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>(),
            )
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

    private fun createKandidat() {
        val endring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).copy(createdAt = nowUTC().minusDays(3))
        database.createDialogmotekandidatEndring(endring)
    }

    private fun createUnntak(): Unntak {
        val unntak = generateNewUnntakDTO(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).toUnntak(UserConstants.VEILEDER_IDENT)
        runBlocking {
            database.connection.use {
                it.createDialogmotekandidatEndring(DialogmotekandidatEndring.unntak(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))
                vurderingRepository.createUnntak(it, unntak)
                it.commit()
            }
        }
        return unntak
    }

    private fun createIkkeAktuell(): IkkeAktuell {
        val ikkeAktuell = generateNewIkkeAktuellDTO(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).toIkkeAktuell(UserConstants.VEILEDER_IDENT)
        database.connection.use {
            it.createDialogmotekandidatEndring(DialogmotekandidatEndring.ikkeAktuell(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))
            runBlocking { vurderingRepository.createIkkeAktuell(connection = it, commit = false, ikkeAktuell = ikkeAktuell) }
            it.commit()
        }
        return ikkeAktuell
    }

    @Test
    fun `returns empty historikk for person not kandidat`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val historikk = response.body<List<HistorikkDTO>>()
        assertEquals(0, historikk.size)
    }

    @Test
    fun `returns historikk for person kandidat then dialogmøte ferdigstilt`() = testApplication {
        val client = setupApiAndClient()
        createKandidat()
        database.createDialogmotekandidatEndring(DialogmotekandidatEndring.ferdigstiltDialogmote(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val historikk = response.body<List<HistorikkDTO>>()
        assertEquals(1, historikk.size)
        assertEquals(HistorikkType.KANDIDAT, historikk[0].type)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, historikk[0].arsak)
        assertNull(historikk[0].vurdertAv)
    }

    @Test
    fun `returns historikk for person kandidat then lukket`() = testApplication {
        val client = setupApiAndClient()
        createKandidat()
        database.createDialogmotekandidatEndring(DialogmotekandidatEndring.lukket(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER))
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val historikk = response.body<List<HistorikkDTO>>()
        assertEquals(2, historikk.size)
        assertEquals(HistorikkType.LUKKET, historikk[0].type)
        assertEquals(DialogmotekandidatEndringArsak.LUKKET.name, historikk[0].arsak)
        assertNull(historikk[0].vurdertAv)
        assertEquals(HistorikkType.KANDIDAT, historikk[1].type)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, historikk[1].arsak)
        assertNull(historikk[1].vurdertAv)
    }

    @Test
    fun `returns historikk for person kandidat then unntak`() = testApplication {
        val client = setupApiAndClient()
        createKandidat()
        val unntak = createUnntak()
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val historikk = response.body<List<HistorikkDTO>>()
        assertEquals(2, historikk.size)
        assertEquals(HistorikkType.UNNTAK, historikk[0].type)
        assertEquals(unntak.arsak.name, historikk[0].arsak)
        assertEquals(unntak.createdBy, historikk[0].vurdertAv)
        assertEquals(HistorikkType.KANDIDAT, historikk[1].type)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, historikk[1].arsak)
        assertNull(historikk[1].vurdertAv)
    }

    @Test
    fun `returns historikk for person kandidat then ikke aktuell`() = testApplication {
        val client = setupApiAndClient()
        createKandidat()
        val ikkeAktuell = createIkkeAktuell()
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val historikk = response.body<List<HistorikkDTO>>()
        assertEquals(2, historikk.size)
        assertEquals(HistorikkType.IKKE_AKTUELL, historikk[0].type)
        assertEquals(ikkeAktuell.arsak.name, historikk[0].arsak)
        assertEquals(ikkeAktuell.createdBy, historikk[0].vurdertAv)
        assertEquals(HistorikkType.KANDIDAT, historikk[1].type)
        assertEquals(DialogmotekandidatEndringArsak.STOPPUNKT.name, historikk[1].arsak)
        assertNull(historikk[1].vurdertAv)
    }

    @Test
    fun `returns status Unauthorized if no token is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(historikkUrl) {}
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns status Forbidden if denied access to person`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(historikkUrl) { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get(historikkUrl) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
