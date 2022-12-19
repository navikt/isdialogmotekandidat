package no.nav.syfo.unntak

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.unntak.api.domain.toUnntak
import no.nav.syfo.unntak.database.createUnntak
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UnntakServiceSpek : Spek({
    describe("UnntakService") {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val oppfolgingstilfelleServiceMock = mockk<OppfolgingstilfelleService>(relaxed = true)
            val dialogmotekandidatServiceMock = mockk<DialogmotekandidatService>(relaxed = true)

            val unntakService = UnntakService(
                database = database,
                dialogmotekandidatService = dialogmotekandidatServiceMock,
                oppfolgingstilfelleService = oppfolgingstilfelleServiceMock,
            )

            afterEachTest {
                database.dropData()
                clearMocks(oppfolgingstilfelleServiceMock, dialogmotekandidatServiceMock)
            }

            fun createUnntak(personIdentNumber: PersonIdentNumber) {
                val newUnntakDTO = generateNewUnntakDTO(personIdent = personIdentNumber)
                val unntak = newUnntakDTO.toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
                database.connection.use {
                    it.createUnntak(unntak)
                    it.commit()
                }
            }

            describe("getUnntakStatistikk") {
                it("Gets oppfolgingstilfeller once for each person with unntak") {
                    createUnntak(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
                    createUnntak(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT)
                    createUnntak(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)

                    runBlocking {
                        unntakService.getUnntakStatistikk(
                            veilederIdent = UserConstants.VEILEDER_IDENT,
                            veilederToken = "asda",
                            callId = "andsa"
                        )
                    }

                    coVerify(exactly = 2) {
                        oppfolgingstilfelleServiceMock.getAllOppfolgingstilfeller(
                            any(),
                            any(),
                            any()
                        )
                    }
                }
            }
        }
    }
})
