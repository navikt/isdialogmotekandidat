package no.nav.syfo.dialogmotekandidat.midlertidig

import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

const val queryCreateMidlertidigDialogmotekandidatEndring =
    """
    INSERT INTO MIDLERTIDIG_DIALOGMOTEKANDIDAT_ENDRING (
        id,
        uuid,
        created_at,
        personident,
        kandidat,
        arsak
    ) values (DEFAULT, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createMidlertidigDialogmotekandidatEndring(
    midlertidigDialogmotekandidatEndring: DialogmotekandidatEndring,
) {
    val idList = this.prepareStatement(queryCreateMidlertidigDialogmotekandidatEndring).use {
        it.setString(1, midlertidigDialogmotekandidatEndring.uuid.toString())
        it.setObject(2, midlertidigDialogmotekandidatEndring.createdAt)
        it.setString(3, midlertidigDialogmotekandidatEndring.personIdentNumber.value)
        it.setBoolean(4, midlertidigDialogmotekandidatEndring.kandidat)
        it.setString(5, midlertidigDialogmotekandidatEndring.arsak.name)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating MIDLERTIDIG_DIALOGMOTEKANDIDAT_ENDRING failed, no rows affected.")
    }
}

const val queryGetMidlertidigDialogmotekandidatEndringForPerson =
    """
        SELECT * 
        FROM MIDLERTIDIG_DIALOGMOTEKANDIDAT_ENDRING
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

fun Connection.getMidlertidigDialogmotekandidatEndringListForPerson(
    personIdent: PersonIdentNumber,
): List<PMidlertidigDialogmotekandidatEndring> =
    prepareStatement(queryGetMidlertidigDialogmotekandidatEndringForPerson).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPDialogmotekandidatEndringList() }
    }

fun ResultSet.toPDialogmotekandidatEndringList() =
    PMidlertidigDialogmotekandidatEndring(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personIdent = PersonIdentNumber(getString("personident")),
        kandidat = getBoolean("kandidat"),
        arsak = getString("arsak"),
    )
