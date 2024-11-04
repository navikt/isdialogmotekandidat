package no.nav.syfo.cronjob.dialogmotekandidat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.syfo.dialogmotekandidat.api.HistorikkDTO
import no.nav.syfo.dialogmotekandidat.api.HistorikkType
import no.nav.syfo.dialogmotekandidat.api.kandidatApiBasePath
import no.nav.syfo.dialogmotekandidat.api.kandidatApiHistorikkPath
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
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
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
        testApplication {
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database

            application {
                testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>(),
                )
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
                        runTest {
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                                }
                            }
                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val historikk = objectMapper.readValue<List<HistorikkDTO>>(response.bodyAsText())
                            historikk.size shouldBeEqualTo 0
                        }
                    }

                    it("returns historikk for person kandidat then dialogmøte ferdigstilt") {
                        runTest {
                            createKandidat()
                            database.createDialogmotekandidatEndring(
                                DialogmotekandidatEndring.ferdigstiltDialogmote(
                                    personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                                )
                            )
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                                }
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
                        runTest {
                            createKandidat()
                            database.createDialogmotekandidatEndring(
                                DialogmotekandidatEndring.lukket(
                                    personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                                )
                            )
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                                }
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
                        runTest {
                            createKandidat()
                            createUnntak()
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                                }
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
                        runTest {
                            createKandidat()
                            createIkkeAktuell()
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value
                                }
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
                        runTest {
                            val response = client.get(historikkUrl) {}
                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        runTest {
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value
                                }
                            }
                            response.status shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        runTest {
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                }
                            }
                            response.status shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        runTest {
                            val response = client.get(historikkUrl) {
                                headers {
                                    HttpHeaders.Authorization to bearerHeader(validToken)
                                    NAV_PERSONIDENT_HEADER to UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1)
                                }
                            }
                            response.status shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
