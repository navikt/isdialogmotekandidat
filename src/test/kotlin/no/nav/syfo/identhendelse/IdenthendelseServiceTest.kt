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
    private val vurderingRepository = externalMockEnvironment.dialogmotekandidatVurderingRepository
    private val azureAdClient = AzureAdClient(externalMockEnvironment.environment.azure, externalMockEnvironment.mockHttpClient)
    private val pdlClient = PdlClient(azureAdClient, externalMockEnvironment.environment.clients.pdl, externalMockEnvironment.mockHttpClient)
    private val identhendelseService = IdenthendelseService(database, pdlClient)

    @BeforeEach
    fun setup() { database.dropData() }

    private fun populateDatabase(oldIdent: Personident, database: DatabaseInterface, updateInAllTables: Boolean = true) {
        val stoppunkt = generateDialogmotekandidatStoppunktPlanlagt(oldIdent, LocalDate.now())
        val endring = generateDialogmotekandidatEndringStoppunkt(oldIdent)
        database.connection.createDialogmotekandidatStoppunkt(true, stoppunkt)
        database.createDialogmotekandidatEndring(endring)
        if (updateInAllTables) {
            val unntak = generateNewUnntakDTO(oldIdent).toUnntak(createdByIdent = UserConstants.VEILEDER_IDENT)
            val moteTidspunkt = OffsetDateTime.now().minusDays(1)
            val kStatusEndring = generateKDialogmoteStatusEndring(oldIdent, DialogmoteStatusEndringType.INNKALT, moteTidspunkt, moteTidspunkt)
            val statusEndring = DialogmoteStatusEndring.create(kStatusEndring)
            runBlocking { database.connection.use { vurderingRepository.createUnntak(it, unntak); it.commit() } }
            database.connection.createDialogmoteStatus(true, statusEndring)
        }
    }

    @Test
    fun `Skal oppdatere gamle identer når person har fått ny ident`() {
        val identhendelse = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val newIdent = identhendelse.getActivePersonident()!!
        val oldIdenter = identhendelse.getInactivePersonidenter()
        populateDatabase(oldIdenter.first(), database)
        runBlocking { identhendelseService.handleIdenthendelse(identhendelse) }
        val newOccurrences = database.getIdentCount(listOf(newIdent))
        assertEquals(4, newOccurrences)
    }

    @Test
    fun `Skal oppdatere gamle identer når person har fått ny ident, men kun tabeller som har forekomst`() {
        val identhendelse = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val newIdent = identhendelse.getActivePersonident()!!
        val oldIdenter = identhendelse.getInactivePersonidenter()
        populateDatabase(oldIdenter.first(), database, updateInAllTables = false)
        runBlocking { identhendelseService.handleIdenthendelse(identhendelse) }
        val newOccurrences = database.getIdentCount(listOf(newIdent))
        assertEquals(2, newOccurrences)
    }

    @Test
    fun `Skal kaste feil hvis PDL ikke har oppdatert identen`() {
        val identhendelse = generateKafkaIdenthendelseDTO(personident = UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER, hasOldPersonident = true)
        val oldIdent = identhendelse.getInactivePersonidenter().first()
        populateDatabase(oldIdent, database)
        assertThrows(IllegalStateException::class.java) { identhendelseService.handleIdenthendelse(identhendelse) }
    }
}
