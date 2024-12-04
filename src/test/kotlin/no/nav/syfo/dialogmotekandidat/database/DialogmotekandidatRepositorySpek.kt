package no.nav.syfo.dialogmotekandidat.database

import io.ktor.server.testing.*
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmotekandidatRepositorySpek : Spek({

    val kandidatPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER

    describe(DialogmotekandidatRepositorySpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val dialogmotekandidatRepository = DialogmotekandidatRepository(database)

        afterEachTest {
            database.dropData()
        }

        describe("Successfully gets a dialogmotekandidatendring") {
            testApplication {
                val dialogmoteKandidatEndring = generateDialogmotekandidatEndringStoppunkt(kandidatPersonIdent)
                database.connection.use { connection ->
                    connection.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmoteKandidatEndring)
                    connection.commit()
                }

                val insertedDialogmotekandidatEndring =
                    dialogmotekandidatRepository.getDialogmotekandidatEndring(dialogmoteKandidatEndring.uuid)
                insertedDialogmotekandidatEndring?.personIdent shouldBeEqualTo dialogmoteKandidatEndring.personIdentNumber
                insertedDialogmotekandidatEndring?.uuid shouldBeEqualTo dialogmoteKandidatEndring.uuid
                insertedDialogmotekandidatEndring?.arsak shouldBeEqualTo dialogmoteKandidatEndring.arsak.toString()
                insertedDialogmotekandidatEndring?.kandidat shouldBeEqualTo dialogmoteKandidatEndring.kandidat
            }
        }
    }
})
