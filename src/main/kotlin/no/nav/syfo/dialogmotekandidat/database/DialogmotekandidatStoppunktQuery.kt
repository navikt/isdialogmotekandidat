package no.nav.syfo.dialogmotekandidat.database

import no.nav.syfo.application.database.*
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.sql.Date
import java.util.*

const val queryCreateDialogmotekandidatStoppunkt =
    """
    INSERT INTO DIALOGMOTEKANDIDAT_STOPPPUNKT (
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
    val idList = this.prepareStatement(queryCreateDialogmotekandidatStoppunkt).use {
        it.setString(1, dialogmotekandidatStoppunkt.uuid.toString())
        it.setTimestamp(2, Timestamp.from(dialogmotekandidatStoppunkt.createdAt.toInstant()))
        it.setString(3, dialogmotekandidatStoppunkt.personIdent.value)
        it.setDate(4, Date.valueOf(dialogmotekandidatStoppunkt.stoppunktPlanlagt))
        it.setString(5, dialogmotekandidatStoppunkt.status.name)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating DIALOGMOTEKANDIDAT_STOPPPUNKT failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }
}

const val queryGetDialogmotekandidatStoppunkt =
    """
        SELECT *
        FROM DIALOGMOTEKANDIDAT_STOPPPUNKT
        WHERE personident = ?
        ORDER BY stoppunkt_planlagt DESC;
    """

fun DatabaseInterface.getDialogmotekandidatStoppunktList(
    arbeidstakerPersonIdent: PersonIdentNumber,
): List<PDialogmotekandidatStoppunkt> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetDialogmotekandidatStoppunkt).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList { toPDialogmotekandidatStoppunktList() }
        }
    }

fun ResultSet.toPDialogmotekandidatStoppunktList(): PDialogmotekandidatStoppunkt =
    PDialogmotekandidatStoppunkt(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
        personIdent = PersonIdentNumber(getString("personident")),
        processedAt = getTimestamp("processed_at")?.toOffsetDateTimeUTC(),
        status = getString("status"),
        stoppunktPlanlagt = getDate("stoppunkt_planlagt").toLocalDate(),
    )
