package no.nav.syfo.testhelper

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.createDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndringList
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.isLatestIkkeKandidat
import no.nav.syfo.domain.PersonIdentNumber
import org.flywaydb.core.Flyway
import java.sql.Connection

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        """
        DELETE FROM DIALOGMOTEKANDIDAT_STOPPUNKT
        """.trimIndent(),
        """
        DELETE FROM DIALOGMOTEKANDIDAT_ENDRING
        """.trimIndent(),
        """
        DELETE FROM UNNTAK
        """.trimIndent(),
        """
        DELETE FROM DIALOGMOTESTATUS
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.createDialogmotekandidatEndring(dialogmotekandidatEndring: DialogmotekandidatEndring) {
    this.connection.use { connection ->
        connection.createDialogmotekandidatEndring(dialogmotekandidatEndring)
        connection.commit()
    }
}

fun DatabaseInterface.isIkkeKandidat(personIdentNumber: PersonIdentNumber) =
    connection.getDialogmotekandidatEndringListForPerson(personIdent = personIdentNumber)
        .toDialogmotekandidatEndringList()
        .isLatestIkkeKandidat()

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}
