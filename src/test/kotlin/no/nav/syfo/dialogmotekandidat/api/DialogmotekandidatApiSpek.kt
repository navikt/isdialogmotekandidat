package no.nav.syfo.dialogmotekandidat.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.oppfolgingstilfelle.toOffsetDatetime
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DialogmotekandidatApiSpek : Spek({
    val urlKandidatPersonIdent = "$kandidatApiBasePath/$kandidatApiPersonidentPath"

    describe(DialogmotekandidatApiSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance

        val database = externalMockEnvironment.database
        val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()

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

        beforeEachTest {
            database.dropData()
        }

        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        describe("Get DialogmoteKandidatDTO for person") {
            describe("Happy path") {
                it("returns kandidat=false for person that has never been Kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()
                        dialogmotekandidatDTO.kandidat.shouldBeFalse()
                        dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                    }
                }

                it("returns kandidat=false for person that was generated as Stoppunkt-Kandidat today") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()

                        dialogmotekandidatDTO.kandidat.shouldBeFalse()
                        dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                    }
                }

                it("returns kandidat=false for person that was generated as Stoppunkt-Kandidat 6 days ago") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = LocalDate.now().minusDays(6).toOffsetDatetime(),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()
                        dialogmotekandidatDTO.kandidat.shouldBeFalse()
                        dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                    }
                }
                it("returns kandidat=true for person that was generated as Stoppunkt-Kandidat 7 days ago") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = LocalDate.now().minusDays(7).toOffsetDatetime(),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()
                        dialogmotekandidatDTO.kandidat.shouldBeTrue()
                        dialogmotekandidatDTO.kandidatAt?.toInstant(ZoneOffset.UTC)
                            ?.toEpochMilli() shouldBeEqualTo dialogmotekandidatEndring.createdAt.toLocalDateTimeOslo()
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                    }
                }

                it("returns kandidat=false for person that is Stoppunkt-Kandidat but not in current oppfolgingstilfelle") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = OffsetDateTime.now().minusYears(1),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()
                        dialogmotekandidatDTO.kandidat.shouldBeFalse()
                    }
                }

                it("returns kandidat=false for person that has previously been Kandidat") {
                    testApplication {
                        val client = setupApiAndClient()
                        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndringStoppunkt,
                        )
                        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndringFerdigstilt,
                        )

                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmotekandidatDTO = response.body<DialogmotekandidatDTO>()
                        dialogmotekandidatDTO.kandidat.shouldBeFalse()
                        dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                    }
                }
            }
            describe("Unhappy paths") {
                it("returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlKandidatPersonIdent) {}
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
                it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlKandidatPersonIdent) {
                            bearerAuth(validToken)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlKandidatPersonIdent) {
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
