package no.nav.syfo.dialogmotekandidat.database

import io.ktor.server.testing.testApplication
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DialogmotekandidatRepositoryTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val repository = DialogmotekandidatRepository(database)

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Test
    fun `Successfully gets a dialogmotekandidatendring`() = testApplication {
        val dialogmoteKandidatEndring = generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER)
        database.connection.use { connection ->
            connection.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmoteKandidatEndring)
            connection.commit()
        }
        val inserted = repository.getDialogmotekandidatEndring(dialogmoteKandidatEndring.uuid)
        assertEquals(dialogmoteKandidatEndring.personIdentNumber, inserted?.personident)
        assertEquals(dialogmoteKandidatEndring.uuid, inserted?.uuid)
        assertEquals(dialogmoteKandidatEndring.arsak.toString(), inserted?.arsak)
        assertEquals(dialogmoteKandidatEndring.kandidat, inserted?.kandidat)
    }
}
