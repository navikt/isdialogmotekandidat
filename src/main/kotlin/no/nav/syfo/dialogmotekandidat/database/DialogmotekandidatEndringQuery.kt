package no.nav.syfo.dialogmotekandidat.database

import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

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

fun Connection.getLatestDialogmotekandidatEndringForPerson(
    personIdent: PersonIdentNumber,
): PDialogmotekandidatEndring? = prepareStatement(queryGetDialogmotekandidatEndringForPerson).use {
    it.setString(1, personIdent.value)
    it.executeQuery().toList { toPDialogmotekandidatEndringList() }
}.firstOrNull()

fun ResultSet.toPDialogmotekandidatEndringList() =
    PDialogmotekandidatEndring(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personIdent = PersonIdentNumber(getString("personident")),
        kandidat = getBoolean("kandidat"),
        arsak = getString("arsak"),
    )
