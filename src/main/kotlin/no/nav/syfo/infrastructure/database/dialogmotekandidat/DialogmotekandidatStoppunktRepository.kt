package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.application.ITransaction
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.nowUTC
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatStoppunktRepository(private val database: DatabaseInterface) {

    fun createDialogmotekandidatStoppunkt(
        transaction: ITransaction,
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        if (dialogmotekandidatStoppunkt.processedAt != null || dialogmotekandidatStoppunkt.status != DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT) {
            throw IllegalArgumentException("Cannot create DialogmotekandidatStoppunkt with status ${dialogmotekandidatStoppunkt.status} processedAt ${dialogmotekandidatStoppunkt.processedAt}")
        }

        val idList = transaction.connection.prepareStatement(CREATE_DIALOGMOTEKANDIDAT_STOPPUNKT_QUERY).use {
            it.setString(1, dialogmotekandidatStoppunkt.uuid.toString())
            it.setObject(2, dialogmotekandidatStoppunkt.createdAt)
            it.setString(3, dialogmotekandidatStoppunkt.personident.value)
            it.setDate(4, Date.valueOf(dialogmotekandidatStoppunkt.stoppunktPlanlagt))
            it.setString(5, dialogmotekandidatStoppunkt.status.name)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating DIALOGMOTEKANDIDAT_STOPPUNKT failed, no rows affected.")
        }
    }

    fun getDialogmotekandidatStoppunktList(
        personident: Personident,
    ): List<PDialogmotekandidatStoppunkt> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEKANDIDAT_STOPPUNKT_QUERY).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPDialogmotekandidatStoppunkt() }
            }
        }

    fun updateDialogmotekandidatStoppunktStatus(
        transaction: ITransaction,
        uuid: UUID,
        status: DialogmotekandidatStoppunktStatus,
    ) {
        if (status == DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT) {
            throw IllegalArgumentException("Cannot update to status $status")
        }

        val now = nowUTC()

        transaction.connection.prepareStatement(UPDATE_DIALOGMOTEKANDIDAT_STOPPUNKT_STATUS_QUERY).use { preparedStatement ->
            preparedStatement.setString(1, status.name)
            preparedStatement.setObject(2, now)
            preparedStatement.setString(3, uuid.toString())
            preparedStatement.execute()
        }
    }

    fun getDialogmotekandidaterWithStoppunktTodayOrYesterday(): List<PDialogmotekandidatStoppunkt> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEKANDIDATER_WITH_STOPPUNKT_TODAY_OR_YESTERDAY_QUERY).use { preparedStatement ->
                preparedStatement.setDate(1, Date.valueOf(LocalDate.now()))
                preparedStatement.setDate(2, Date.valueOf(LocalDate.now().minusDays(1)))
                preparedStatement.executeQuery().toList { toPDialogmotekandidatStoppunkt() }
            }
        }

    private fun ResultSet.toPDialogmotekandidatStoppunkt(): PDialogmotekandidatStoppunkt =
        PDialogmotekandidatStoppunkt(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            personident = Personident(getString("personident")),
            processedAt = getObject("processed_at", OffsetDateTime::class.java),
            status = getString("status"),
            stoppunktPlanlagt = getDate("stoppunkt_planlagt").toLocalDate(),
        )

    companion object {
        private const val CREATE_DIALOGMOTEKANDIDAT_STOPPUNKT_QUERY =
            """
            INSERT INTO DIALOGMOTEKANDIDAT_STOPPUNKT (
                id,
                uuid,
                created_at,
                personident,
                stoppunkt_planlagt,
                status
            ) values (DEFAULT, ?, ?, ?, ?, ?)
            RETURNING id
            """

        private const val GET_DIALOGMOTEKANDIDAT_STOPPUNKT_QUERY =
            """
            SELECT *
            FROM DIALOGMOTEKANDIDAT_STOPPUNKT
            WHERE personident = ?
            ORDER BY stoppunkt_planlagt DESC;
            """

        private const val UPDATE_DIALOGMOTEKANDIDAT_STOPPUNKT_STATUS_QUERY =
            """
            UPDATE DIALOGMOTEKANDIDAT_STOPPUNKT SET status=?, processed_at=? WHERE uuid = ?
            """

        private const val GET_DIALOGMOTEKANDIDATER_WITH_STOPPUNKT_TODAY_OR_YESTERDAY_QUERY =
            """
            SELECT *
            FROM DIALOGMOTEKANDIDAT_STOPPUNKT
            WHERE (stoppunkt_planlagt = ? OR stoppunkt_planlagt = ?) AND processed_at IS NULL
            """
    }
}
