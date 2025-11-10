package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.toUnntak
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.infrastructure.database.getIdentCount
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class IdenthendelseServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmotekandidatVurderingRepository = externalMockEnvironment.dialogmotekandidatVurderingRepository
    private val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    private val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )

    private fun populateDatabase(oldIdent: Personident, database: DatabaseInterface, updateInAllTables: Boolean = true) {
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

            runBlocking {
                database.connection.use {
                    dialogmotekandidatVurderingRepository.createUnntak(it, unntak)
                    it.commit()
                }
            }
            database.connection.createDialogmoteStatus(
                commit = true,
                dialogmoteStatusEndring = status,
            )
        }
    }

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    @Test
    fun `Skal oppdatere gamle identer når person har fått ny ident`() {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
        val oldIdenter = kafkaIdenthendelseDTO.getInactivePersonidenter()

        populateDatabase(oldIdenter.first(), database)

        runBlocking {
            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
        }

        val newIdentOccurrences = database.getIdentCount(listOf(newIdent))
        assertEquals(4, newIdentOccurrences)
    }

    @Test
    fun `Skal oppdatere gamle identer når person har fått ny ident, men kun tabeller som har en forekomst av gamle identer`() {
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
        assertEquals(2, newIdentOccurrences)
    }

    @Test
    fun `Skal kaste feil hvis PDL ikke har oppdatert identen`() {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
            personident = UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER,
            hasOldPersonident = true,
        )
        val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

        populateDatabase(oldIdent, database)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }
        }
    }
}
