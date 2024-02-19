package no.nav.syfo.cronjob.dialogmotekandidat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.dialogmotekandidat.api.*
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
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlKandidatPersonIdent = "$kandidatApiBasePath/$kandidatApiPersonidentPath"

    describe(DialogmotekandidatApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            )

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
                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeFalse()
                            dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                        }
                    }

                    it("returns kandidat=false for person that was generated as Stoppunkt-Kandidat today") {
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeFalse()
                            dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                        }
                    }

                    it("returns kandidat=false for person that was generated as Stoppunkt-Kandidat 6 days ago") {
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = LocalDate.now().minusDays(6).toOffsetDatetime(),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeFalse()
                            dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                        }
                    }
                    it("returns kandidat=true for person that was generated as Stoppunkt-Kandidat 7 days ago") {
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = LocalDate.now().minusDays(7).toOffsetDatetime(),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeTrue()
                            dialogmotekandidatDTO.kandidatAt?.toInstant(ZoneOffset.UTC)
                                ?.toEpochMilli() shouldBeEqualTo dialogmotekandidatEndring.createdAt.toLocalDateTimeOslo()
                                .toInstant(ZoneOffset.UTC)
                                .toEpochMilli()
                        }
                    }

                    it("returns kandidat=false for person that is Stoppunkt-Kandidat but not in current oppfolgingstilfelle") {
                        val dialogmotekandidatEndring = generateDialogmotekandidatEndringStoppunkt(
                            personIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                        ).copy(
                            createdAt = OffsetDateTime.now().minusYears(1),
                        )
                        database.createDialogmotekandidatEndring(
                            dialogmotekandidatEndring = dialogmotekandidatEndring,
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeFalse()
                        }
                    }

                    it("returns kandidat=false for person that has previously been Kandidat") {
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

                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmotekandidatDTO =
                                objectMapper.readValue<DialogmotekandidatDTO>(response.content!!)

                            dialogmotekandidatDTO.kandidat.shouldBeFalse()
                            dialogmotekandidatDTO.kandidatAt.shouldBeNull()
                        }
                    }
                }
                describe("Unhappy paths") {
                    it("returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlKandidatPersonIdent) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1)
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
