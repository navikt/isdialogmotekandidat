package no.nav.syfo.dialogmotekandidat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.dialogmotekandidat.database.createDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.ikkeaktuell.api.domain.toIkkeAktuell
import no.nav.syfo.ikkeaktuell.api.generateNewIkkeAktuellDTO
import no.nav.syfo.ikkeaktuell.database.createIkkeAktuell
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.unntak.api.domain.toUnntak
import no.nav.syfo.unntak.database.createUnntak
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

val unntak = generateNewUnntakDTO(
    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
).toUnntak(
    createdByIdent = UserConstants.VEILEDER_IDENT
)
val ikkeAktuell = generateNewIkkeAktuellDTO(
    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
).toIkkeAktuell(
    createdByIdent = UserConstants.VEILEDER_IDENT
)

class DialogmotekandidatHistorikkApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val historikkUrl = "$kandidatApiBasePath/$kandidatApiHistorikkPath"

    describe(DialogmotekandidatHistorikkApiSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
            application {
                testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>(),
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
        }

        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )

        fun createKandidat() {
            val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
            ).copy(
                createdAt = nowUTC().minusDays(3)
            )
            database.createDialogmotekandidatEndring(
                dialogmotekandidatEndring = dialogmotekandidatEndring,
            )
        }

        fun createUnntak() {
            database.connection.use {
                it.createDialogmotekandidatEndring(
                    DialogmotekandidatEndring.unntak(
                        personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                    )
                )
                it.createUnntak(unntak)
                it.commit()
            }
        }

        fun createIkkeAktuell() {
            database.connection.use {
                it.createDialogmotekandidatEndring(
                    DialogmotekandidatEndring.ikkeAktuell(
                        personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                    )
                )
                it.createIkkeAktuell(ikkeAktuell)
                it.commit()
            }
        }

        describe("Get historikk for person") {
            describe("Happy path") {
                it("returns empty historikk for person not kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                        historikk.size shouldBeEqualTo 0
                    }
                }

                it("returns historikk for person kandidat then dialogmøte ferdigstilt") {
                    testApplication {
                        val client = setupApiAndClient()
                        createKandidat()
                        database.createDialogmotekandidatEndring(
                            DialogmotekandidatEndring.ferdigstiltDialogmote(
                                personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                            )
                        )
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                        historikk.size shouldBeEqualTo 1

                        historikk[0].type shouldBeEqualTo HistorikkType.KANDIDAT
                        historikk[0].arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
                        historikk[0].vurdertAv.shouldBeNull()
                    }
                }

                it("returns historikk for person kandidat then lukket") {
                    testApplication {
                        val client = setupApiAndClient()
                        createKandidat()
                        database.createDialogmotekandidatEndring(
                            DialogmotekandidatEndring.lukket(
                                personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                            )
                        )
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                        historikk.size shouldBeEqualTo 2

                        historikk[0].type shouldBeEqualTo HistorikkType.LUKKET
                        historikk[0].arsak shouldBeEqualTo DialogmotekandidatEndringArsak.LUKKET.name
                        historikk[0].vurdertAv.shouldBeNull()

                        historikk[1].type shouldBeEqualTo HistorikkType.KANDIDAT
                        historikk[1].arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
                        historikk[1].vurdertAv.shouldBeNull()
                    }
                }

                it("returns historikk for person kandidat then unntak") {
                    testApplication {
                        val client = setupApiAndClient()
                        createKandidat()
                        createUnntak()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                        historikk.size shouldBeEqualTo 2

                        historikk[0].type shouldBeEqualTo HistorikkType.UNNTAK
                        historikk[0].arsak shouldBeEqualTo unntak.arsak.name
                        historikk[0].vurdertAv shouldBeEqualTo unntak.createdBy

                        historikk[1].type shouldBeEqualTo HistorikkType.KANDIDAT
                        historikk[1].arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
                        historikk[1].vurdertAv.shouldBeNull()
                    }
                }

                it("returns historikk for person kandidat then ikke aktuell") {
                    testApplication {
                        val client = setupApiAndClient()
                        createKandidat()
                        createIkkeAktuell()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                        historikk.size shouldBeEqualTo 2

                        historikk[0].type shouldBeEqualTo HistorikkType.IKKE_AKTUELL
                        historikk[0].arsak shouldBeEqualTo ikkeAktuell.arsak.name
                        historikk[0].vurdertAv shouldBeEqualTo ikkeAktuell.createdBy

                        historikk[1].type shouldBeEqualTo HistorikkType.KANDIDAT
                        historikk[1].arsak shouldBeEqualTo DialogmotekandidatEndringArsak.STOPPUNKT.name
                        historikk[1].vurdertAv.shouldBeNull()
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(historikkUrl) {}
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
                it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(historikkUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
