package no.nav.syfo.testhelper

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatEndring
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
        """
        DELETE FROM IKKE_AKTUELL
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

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}
