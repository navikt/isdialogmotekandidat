package no.nav.syfo.dialogmotekandidat.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.api.GetDialogmotekandidatForPersonsResponseDTO
import no.nav.syfo.api.GetDialogmotekandidaterRequestDTO
import no.nav.syfo.api.endpoints.kandidatApiBasePath
import no.nav.syfo.api.endpoints.kandidatApiPersonidentPath
import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.Dialogmotekandidat
import no.nav.syfo.infrastructure.database.DialogmotekandidatVurderingRepository
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_2_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import no.nav.syfo.util.toLocalDateTimeOslo
import no.nav.syfo.util.toOffsetDatetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DialogmotekandidatApiTest {
    private val urlKandidatPersonIdent = "$kandidatApiBasePath/$kandidatApiPersonidentPath"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val endringProducer = mockk<DialogmotekandidatEndringProducer>()
    private val dialogmotekandidatVurderingRepository = DialogmotekandidatVurderingRepository(database)

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
        navIdent = VEILEDER_IDENT,
    )

    @Nested
    @DisplayName("GET kandidat")
    inner class GetKandidat {
        @Test
        fun `returns kandidat=false for person that has never been Kandidat`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlKandidatPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val kandidat = response.body<Dialogmotekandidat>()
            assertFalse(kandidat.kandidat)
            assertNull(kandidat.kandidatAt)
        }

        @Test
        fun `returns kandidat=false for person generated as Stoppunkt-Kandidat today`() = testApplication {
            val client = setupApiAndClient()
            val endring = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(endring)
            val response = client.get(urlKandidatPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val kandidat = response.body<Dialogmotekandidat>()
            assertFalse(kandidat.kandidat)
            assertNull(kandidat.kandidatAt)
        }

        @Test
        fun `returns kandidat=false for person generated as Stoppunkt-Kandidat 6 days ago`() = testApplication {
            val client = setupApiAndClient()
            val endring = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER).copy(
                createdAt = LocalDate.now().minusDays(6).toOffsetDatetime()
            )
            database.createDialogmotekandidatEndring(endring)
            val response = client.get(urlKandidatPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val kandidat = response.body<Dialogmotekandidat>()
            assertFalse(kandidat.kandidat)
            assertNull(kandidat.kandidatAt)
        }

        @Test
        fun `returns kandidat=true for person generated as Stoppunkt-Kandidat 7 days ago`() = testApplication {
            val client = setupApiAndClient()
            val endring = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER).copy(
                createdAt = LocalDate.now().minusDays(7).toOffsetDatetime()
            )
            database.createDialogmotekandidatEndring(endring)
            val response = client.get(urlKandidatPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
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
        fun `returns kandidat=false for person Stoppunkt-Kandidat not in current oppfolgingstilfelle`() =
            testApplication {
                val client = setupApiAndClient()
                val endring =
                    generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER).copy(
                        createdAt = OffsetDateTime.now().minusYears(1)
                    )
                database.createDialogmotekandidatEndring(endring)
                val response = client.get(urlKandidatPersonIdent) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val kandidat = response.body<Dialogmotekandidat>()
                assertFalse(kandidat.kandidat)
            }

        @Test
        fun `returns kandidat=false for person previously Kandidat`() = testApplication {
            val client = setupApiAndClient()
            val stoppunkt = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(stoppunkt)
            val ferdigstilt = generateDialogmotekandidatEndringFerdigstilt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(ferdigstilt)
            val response = client.get(urlKandidatPersonIdent) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
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
                header(NAV_PERSONIDENT_HEADER, PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
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
        fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() =
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(urlKandidatPersonIdent) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
    }

    @Nested
    @DisplayName("Bulk endepunkt POST kandidater")
    inner class BulkEndpointKandidater {
        val urlGetKandidater = "$kandidatApiBasePath/get-kandidater"

        @Test
        fun `returns dialogmotekandidater without avvent-vurderinger`() = testApplication {
            val client = setupApiAndClient()

            val stoppunktKandidat1 = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            val stoppunktKandidat2 = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_2_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(stoppunktKandidat1)
            database.createDialogmotekandidatEndring(stoppunktKandidat2)

            val requestDTO = GetDialogmotekandidaterRequestDTO(
                personidenter = listOf(
                    ARBEIDSTAKER_PERSONIDENTNUMBER.value,
                    ARBEIDSTAKER_2_PERSONIDENTNUMBER.value
                )
            )

            val response = client.post(urlGetKandidater) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.body<GetDialogmotekandidatForPersonsResponseDTO>()

            assertTrue(responseBody.dialogmotekandidater.containsKey(ARBEIDSTAKER_PERSONIDENTNUMBER.value))
            assertTrue(responseBody.dialogmotekandidater.containsKey(ARBEIDSTAKER_2_PERSONIDENTNUMBER.value))
            assertTrue(responseBody.dialogmotekandidater.values.all { it.isKandidat })
            assertTrue(responseBody.dialogmotekandidater.values.all { it.avvent == null })
        }

        @Test
        fun `returns dialogmotekandidater with avvent-vurderinger`() = testApplication {
            val client = setupApiAndClient()

            val stoppunktKandidat1 = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            val stoppunktKandidat2 = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_2_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(stoppunktKandidat1)
            database.createDialogmotekandidatEndring(stoppunktKandidat2)

            val avvent1 = Avvent(
                frist = LocalDate.now().plusDays(1),
                createdBy = VEILEDER_IDENT,
                personident = ARBEIDSTAKER_PERSONIDENTNUMBER,
                beskrivelse = "Beskrivelse av avvent",
            )
            val avvent2 = Avvent(
                frist = LocalDate.now().plusDays(1),
                createdBy = VEILEDER_IDENT,
                personident = ARBEIDSTAKER_2_PERSONIDENTNUMBER,
                beskrivelse = "Beskrivelse av avvent 2",
            )

            database.connection.use { connection ->
                dialogmotekandidatVurderingRepository.createAvvent(
                    connection = connection,
                    avvent = avvent1,
                )
                dialogmotekandidatVurderingRepository.createAvvent(
                    connection = connection,
                    avvent = avvent2,
                )
                connection.commit()
            }

            val requestDTO = GetDialogmotekandidaterRequestDTO(
                personidenter = listOf(
                    ARBEIDSTAKER_PERSONIDENTNUMBER.value,
                    ARBEIDSTAKER_2_PERSONIDENTNUMBER.value
                )
            )

            val response = client.post(urlGetKandidater) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.body<GetDialogmotekandidatForPersonsResponseDTO>()

            assertTrue(responseBody.dialogmotekandidater.containsKey(ARBEIDSTAKER_PERSONIDENTNUMBER.value))
            assertTrue(responseBody.dialogmotekandidater.containsKey(ARBEIDSTAKER_2_PERSONIDENTNUMBER.value))
            assertTrue(responseBody.dialogmotekandidater.values.all { it.isKandidat })
            assertTrue(responseBody.dialogmotekandidater.values.none { it.avvent == null })
        }

        @Test
        fun `returns correct avvent-vurdering for person when several avvent`() = testApplication {
            val client = setupApiAndClient()

            val stoppunktKandidat = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(stoppunktKandidat)

            val avvent = Avvent(
                frist = LocalDate.now().plusDays(1),
                createdBy = VEILEDER_IDENT,
                personident = ARBEIDSTAKER_PERSONIDENTNUMBER,
                beskrivelse = "Beskrivelse av avvent",
            )
            val oldAvvent = Avvent(
                frist = LocalDate.now().plusDays(10),
                createdBy = VEILEDER_IDENT,
                personident = ARBEIDSTAKER_PERSONIDENTNUMBER,
                beskrivelse = "Gammel avvent",
            ).copy(
                createdAt = OffsetDateTime.now().minusDays(10),
            )

            database.connection.use { connection ->
                dialogmotekandidatVurderingRepository.createAvvent(
                    connection = connection,
                    avvent = avvent,
                )
                dialogmotekandidatVurderingRepository.createAvvent(
                    connection = connection,
                    avvent = oldAvvent,
                )
                connection.commit()
            }

            val requestDTO = GetDialogmotekandidaterRequestDTO(personidenter = listOf(ARBEIDSTAKER_PERSONIDENTNUMBER.value))

            val response = client.post(urlGetKandidater) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.body<GetDialogmotekandidatForPersonsResponseDTO>()

            assertTrue(responseBody.dialogmotekandidater.containsKey(ARBEIDSTAKER_PERSONIDENTNUMBER.value))
            assertTrue(responseBody.dialogmotekandidater.values.all { it.isKandidat })
            assertEquals(1, responseBody.dialogmotekandidater.values.size)
            assertEquals("Beskrivelse av avvent", responseBody.dialogmotekandidater.values.single().avvent?.beskrivelse)
            assertEquals(LocalDate.now().plusDays(1), responseBody.dialogmotekandidater.values.single().avvent?.frist)
        }

        @Test
        fun `returns no dialogmotekandidat when no longer kandidat`() = testApplication {
            val client = setupApiAndClient()

            val ferdigstiltKandidat = generateDialogmotekandidatEndringFerdigstilt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            database.createDialogmotekandidatEndring(ferdigstiltKandidat)

            val requestDTO = GetDialogmotekandidaterRequestDTO(listOf(ARBEIDSTAKER_PERSONIDENTNUMBER.value))

            val response = client.post(urlGetKandidater) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

        @Test
        fun `returns 204 No Content when veileder does not have access`() = testApplication {
            val client = setupApiAndClient()

            val stoppunktKandidat = generateDialogmotekandidatEndringStoppunkt(PERSONIDENTNUMBER_VEILEDER_NO_ACCESS)
            database.createDialogmotekandidatEndring(stoppunktKandidat)

            val requestDTO = GetDialogmotekandidaterRequestDTO(listOf(PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value))

            val response = client.post(urlGetKandidater) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }
    }
}
