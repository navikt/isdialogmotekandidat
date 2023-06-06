package no.nav.syfo.unntak

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewUnntakDTO
import no.nav.syfo.unntak.api.domain.toUnntak
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

            beforeEachTest {
                database.dropData()
                clearMocks(oppfolgingstilfelleServiceMock, dialogmotekandidatServiceMock)
            }

            describe("getUnntakStatistikk") {
                it("Gets oppfolgingstilfeller for all persons with unntak") {
                    val unntakList = listOf(
                        generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).toUnntak(
                            createdByIdent = UserConstants.VEILEDER_IDENT
                        ),
                        generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT).toUnntak(
                            createdByIdent = UserConstants.VEILEDER_IDENT
                        ),
                        generateNewUnntakDTO(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER).toUnntak(
                            createdByIdent = UserConstants.VEILEDER_IDENT
                        )
                    )

                    runBlocking {
                        unntakService.getUnntakStatistikk(
                            unntakList = unntakList,
                            token = "asda",
                            callId = "andsa"
                        )
                    }

                    coVerify(exactly = 1) {
                        oppfolgingstilfelleServiceMock.getAllOppfolgingstilfellerForPersons(
                            listOf(
                                UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
                                UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
                            ),
                            any(),
                            any()
                        )
                    }
                }
            }
        }
    }
})
