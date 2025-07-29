package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

const val queryCreateDialogmotekandidatEndring =
    """
    INSERT INTO DIALOGMOTEKANDIDAT_ENDRING (
        id,
        uuid,
        created_at,
        personident,
        kandidat,
        arsak
    ) values (DEFAULT, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createDialogmotekandidatEndring(
    dialogmotekandidatEndring: DialogmotekandidatEndring,
) {
    val idList = this.prepareStatement(queryCreateDialogmotekandidatEndring).use {
        it.setString(1, dialogmotekandidatEndring.uuid.toString())
        it.setObject(2, dialogmotekandidatEndring.createdAt)
        it.setString(3, dialogmotekandidatEndring.personIdentNumber.value)
        it.setBoolean(4, dialogmotekandidatEndring.kandidat)
        it.setString(5, dialogmotekandidatEndring.arsak.name)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating DIALOGMOTEKANDIDAT_ENDRING failed, no rows affected.")
    }
}

const val queryGetDialogmotekandidatEndringForPerson =
    """
        SELECT * 
        FROM DIALOGMOTEKANDIDAT_ENDRING
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

fun Connection.getDialogmotekandidatEndringListForPerson(
    personIdent: PersonIdentNumber,
): List<PDialogmotekandidatEndring> = prepareStatement(queryGetDialogmotekandidatEndringForPerson).use {
    it.setString(1, personIdent.value)
    it.executeQuery().toList { toPDialogmotekandidatEndringList() }
}

const val queryFindOutdatedDialogmotekandidater =
    """
        select * from dialogmotekandidat_endring d
        where d.created_at = (select max(d2.created_at) from dialogmotekandidat_endring d2 where d2.personident = d.personident)
        and d.created_at < ? and d.kandidat 
        LIMIT 200;
    """

fun DatabaseInterface.findOutdatedDialogmotekandidater(
    cutoff: LocalDateTime,
): List<PDialogmotekandidatEndring> = this.connection.use { connection ->
    connection.prepareStatement(queryFindOutdatedDialogmotekandidater).use {
        it.setTimestamp(1, Timestamp.from(cutoff.toInstantOslo()))
        it.executeQuery().toList { toPDialogmotekandidatEndringList() }
    }
}

fun LocalDateTime.toInstantOslo(): Instant = toInstant(
    ZoneId.of("Europe/Oslo").rules.getOffset(this)
)
