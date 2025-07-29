package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val queryCreateDialogmotekandidatStoppunkt =
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

fun Connection.createDialogmotekandidatStoppunkt(
    commit: Boolean,
    dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
) {
    if (dialogmotekandidatStoppunkt.processedAt != null || dialogmotekandidatStoppunkt.status != DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT) {
        throw IllegalArgumentException("Cannot create DialogmotekandidatStoppunkt with status ${dialogmotekandidatStoppunkt.status} processedAt ${dialogmotekandidatStoppunkt.processedAt}")
    }

    val idList = this.prepareStatement(queryCreateDialogmotekandidatStoppunkt).use {
        it.setString(1, dialogmotekandidatStoppunkt.uuid.toString())
        it.setObject(2, dialogmotekandidatStoppunkt.createdAt)
        it.setString(3, dialogmotekandidatStoppunkt.personIdent.value)
        it.setDate(4, Date.valueOf(dialogmotekandidatStoppunkt.stoppunktPlanlagt))
        it.setString(5, dialogmotekandidatStoppunkt.status.name)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating DIALOGMOTEKANDIDAT_STOPPUNKT failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }
}

const val queryGetDialogmotekandidatStoppunkt =
    """
        SELECT *
        FROM DIALOGMOTEKANDIDAT_STOPPUNKT
        WHERE personident = ?
        ORDER BY stoppunkt_planlagt DESC;
    """

fun DatabaseInterface.getDialogmotekandidatStoppunktList(
    arbeidstakerPersonIdent: Personident,
): List<PDialogmotekandidatStoppunkt> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetDialogmotekandidatStoppunkt).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList { toPDialogmotekandidatStoppunktList() }
        }
    }

const val queryUpdateDialogmotekandidatStoppunktStatus =
    """
        UPDATE DIALOGMOTEKANDIDAT_STOPPUNKT SET status=?, processed_at=? WHERE uuid = ?
    """

fun Connection.updateDialogmotekandidatStoppunktStatus(
    uuid: UUID,
    status: DialogmotekandidatStoppunktStatus,
) {
    if (status == DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT) {
        throw IllegalArgumentException("Cannot update to status $status")
    }

    val now = nowUTC()

    this.prepareStatement(queryUpdateDialogmotekandidatStoppunktStatus).use { preparedStatement ->
        preparedStatement.setString(1, status.name)
        preparedStatement.setObject(2, now)
        preparedStatement.setString(3, uuid.toString())
        preparedStatement.execute()
    }
}

const val queryGetDialogmotekandidaterWithStoppunktTodayOrYesterday =
    """
        SELECT *
        FROM DIALOGMOTEKANDIDAT_STOPPUNKT
        WHERE (stoppunkt_planlagt = ? OR stoppunkt_planlagt = ?) AND processed_at IS NULL
    """

fun DatabaseInterface.getDialogmotekandidaterWithStoppunktTodayOrYesterday(): List<PDialogmotekandidatStoppunkt> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetDialogmotekandidaterWithStoppunktTodayOrYesterday).use { preparedStatement ->
            preparedStatement.setDate(1, Date.valueOf(LocalDate.now()))
            preparedStatement.setDate(2, Date.valueOf(LocalDate.now().minusDays(1)))
            preparedStatement.executeQuery().toList { toPDialogmotekandidatStoppunktList() }
        }
    }

fun ResultSet.toPDialogmotekandidatStoppunktList(): PDialogmotekandidatStoppunkt =
    PDialogmotekandidatStoppunkt(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personIdent = Personident(getString("personident")),
        processedAt = getObject("processed_at", OffsetDateTime::class.java),
        status = getString("status"),
        stoppunktPlanlagt = getDate("stoppunkt_planlagt").toLocalDate(),
    )
