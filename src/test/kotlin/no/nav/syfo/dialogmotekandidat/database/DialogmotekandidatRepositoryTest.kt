package no.nav.syfo.dialogmotekandidat.database

import io.ktor.server.testing.*
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
    private val kandidatPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmotekandidatRepository = DialogmotekandidatRepository(database)

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Test
    fun `Successfully gets a dialogmotekandidatendring`() {
        testApplication {
            val dialogmoteKandidatEndring = generateDialogmotekandidatEndringStoppunkt(kandidatPersonIdent)
            database.connection.use { connection ->
                connection.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmoteKandidatEndring)
                connection.commit()
            }

            val insertedDialogmotekandidatEndring =
                dialogmotekandidatRepository.getDialogmotekandidatEndring(dialogmoteKandidatEndring.uuid)
            assertEquals(dialogmoteKandidatEndring.personIdentNumber, insertedDialogmotekandidatEndring?.personIdent)
            assertEquals(dialogmoteKandidatEndring.uuid, insertedDialogmotekandidatEndring?.uuid)
            assertEquals(dialogmoteKandidatEndring.arsak.toString(), insertedDialogmotekandidatEndring?.arsak)
            assertEquals(dialogmoteKandidatEndring.kandidat, insertedDialogmotekandidatEndring?.kandidat)
        }
    }
}
