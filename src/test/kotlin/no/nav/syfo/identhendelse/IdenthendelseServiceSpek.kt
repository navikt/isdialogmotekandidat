package no.nav.syfo.identhendelse

import kotlinx.coroutines.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.getIdentCount
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.api.toUnntak
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.database.createUnntak
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val azureAdClient = AzureAdClient(
            azureEnvironment = externalMockEnvironment.environment.azure,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        val pdlClient = PdlClient(
            azureAdClient = azureAdClient,
            pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )

        val identhendelseService = IdenthendelseService(
            database = database,
            pdlClient = pdlClient,
        )

        beforeEachTest {
            database.dropData()
        }

        describe("Happy path") {
            it("Skal oppdatere gamle identer når person har fått ny ident") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
                val oldIdenter = kafkaIdenthendelseDTO.getInactivePersonidenter()

                populateDatabase(oldIdenter.first(), database)

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val newIdentOccurrences = database.getIdentCount(listOf(newIdent))
                newIdentOccurrences shouldBeEqualTo 4
            }

            it("Skal oppdatere gamle identer når person har fått ny ident, men kun tabeller som har en forekomst av gamle identer") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
                val oldIdenter = kafkaIdenthendelseDTO.getInactivePersonidenter()

                populateDatabase(
                    oldIdent = oldIdenter.first(),
                    database = database,
                    updateInAllTables = false,
                )

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val newIdentOccurrences = database.getIdentCount(listOf(newIdent))
                newIdentOccurrences shouldBeEqualTo 2
            }
        }

        describe("Unhappy path") {
            it("Skal kaste feil hvis PDL ikke har oppdatert identen") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                    personident = UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER,
                    hasOldPersonident = true,
                )
                val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                populateDatabase(oldIdent, database)

                assertFailsWith(IllegalStateException::class) {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }
            }
        }
    }
})

fun populateDatabase(oldIdent: Personident, database: DatabaseInterface, updateInAllTables: Boolean = true) {
    val stoppunkt = generateDialogmotekandidatStoppunktPlanlagt(oldIdent, LocalDate.now())
    val endring = generateDialogmotekandidatEndringStoppunkt(oldIdent)

    database.connection.createDialogmotekandidatStoppunkt(
        commit = true,
        dialogmotekandidatStoppunkt = stoppunkt,
    )
    database.createDialogmotekandidatEndring(endring)

    if (updateInAllTables) {
        val unntak = generateNewUnntakDTO(oldIdent).toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
        val moteTidspunkt = OffsetDateTime.now().minusDays(1)
        val kafkaDialogmoteStatusEndring = generateKDialogmoteStatusEndring(
            personIdentNumber = oldIdent,
            statusEndringType = DialogmoteStatusEndringType.INNKALT,
            moteTidspunkt = moteTidspunkt,
            endringsTidspunkt = moteTidspunkt,
        )
        val status = DialogmoteStatusEndring.create(kafkaDialogmoteStatusEndring)

        database.connection.use {
            it.createUnntak(unntak)
            it.commit()
        }
        database.connection.createDialogmoteStatus(
            commit = true,
            dialogmoteStatusEndring = status,
        )
    }
}
