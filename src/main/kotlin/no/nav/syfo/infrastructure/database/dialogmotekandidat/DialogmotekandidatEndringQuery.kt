package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection

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
        it.setString(3, dialogmotekandidatEndring.personident.value)
        it.setBoolean(4, dialogmotekandidatEndring.kandidat)
        it.setString(5, dialogmotekandidatEndring.arsak.name)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating DIALOGMOTEKANDIDAT_ENDRING failed, no rows affected.")
    }
}
