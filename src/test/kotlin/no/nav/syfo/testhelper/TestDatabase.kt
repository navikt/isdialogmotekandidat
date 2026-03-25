package no.nav.syfo.testhelper

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.DatabaseTransaction
import no.nav.syfo.infrastructure.database.DialogmoteStatusRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatStoppunktRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.PDialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmoteStatusEndring
import java.time.OffsetDateTime
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
    val repository = DialogmotekandidatRepository(this)
    this.connection.use { connection ->
        repository.createDialogmotekandidatEndring(DatabaseTransaction(connection), dialogmotekandidatEndring)
        connection.commit()
    }
}

fun DatabaseInterface.createDialogmotekandidatStoppunkt(stoppunkt: DialogmotekandidatStoppunkt) {
    val repository = DialogmotekandidatStoppunktRepository(this)
    this.connection.use { connection ->
        repository.createDialogmotekandidatStoppunkt(DatabaseTransaction(connection), stoppunkt)
        connection.commit()
    }
}

fun DatabaseInterface.getDialogmotekandidatStoppunktList(personident: Personident): List<PDialogmotekandidatStoppunkt> =
    DialogmotekandidatStoppunktRepository(this).getDialogmotekandidatStoppunktList(personident)

fun DatabaseInterface.createDialogmoteStatus(dialogmoteStatusEndring: DialogmoteStatusEndring) {
    val repository = DialogmoteStatusRepository(this)
    this.connection.use { connection ->
        repository.createDialogmoteStatus(DatabaseTransaction(connection), dialogmoteStatusEndring)
        connection.commit()
    }
}

fun DatabaseInterface.getLatestDialogmoteFerdigstiltForPerson(personident: Personident): OffsetDateTime? =
    this.connection.use { connection ->
        DialogmoteStatusRepository(this).getLatestDialogmoteFerdigstiltForPerson(DatabaseTransaction(connection), personident)
    }

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}
