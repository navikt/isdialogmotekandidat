package no.nav.syfo.dialogmotestatusendring.database

import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

const val queryCreateDialogmoteStatus =
    """
    INSERT INTO DIALOGMOTESTATUS (
        id,
        uuid,
        created_at,
        personident,
        mote_tidspunkt,
        ferdigstilt_tidspunkt
    ) values (DEFAULT, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createDialogmoteStatus(
    commit: Boolean = false,
    dialogmoteStatusEndring: DialogmoteStatusEndring,
) {
    val idList = this.prepareStatement(queryCreateDialogmoteStatus).use {
        it.setString(1, UUID.randomUUID().toString())
        it.setObject(2, dialogmoteStatusEndring.createdAt)
        it.setString(3, dialogmoteStatusEndring.personIdentNumber.value)
        it.setObject(4, dialogmoteStatusEndring.moteTidspunkt)
        it.setObject(5, dialogmoteStatusEndring.ferdigstiltTidspunkt)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating DIALOGMOTESTATUS failed, no rows affected.")
    }
    if (commit) {
        this.commit()
    }
}

const val queryGetLatestDialogmoteFerdigstiltForPerson =
    """
        SELECT ferdigstilt_tidspunkt 
        FROM DIALOGMOTESTATUS
        WHERE personident = ?
        ORDER BY ferdigstilt_tidspunkt DESC;
    """

fun Connection.getLatestDialogmoteFerdigstiltForPerson(
    personIdent: PersonIdentNumber,
): OffsetDateTime? {
    val dateTimeList = prepareStatement(queryGetLatestDialogmoteFerdigstiltForPerson).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList {
            getObject(1, OffsetDateTime::class.java)
        }
    }
    return dateTimeList.firstOrNull()
}
