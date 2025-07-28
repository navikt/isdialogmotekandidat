package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.IkkeAktuell
import java.sql.Connection

const val queryCreateIkkeAktuell =
    """
    INSERT INTO IKKE_AKTUELL (
        id,
        uuid,
        created_at,
        created_by,
        personident,
        arsak,
        beskrivelse
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createIkkeAktuell(ikkeAktuell: IkkeAktuell) {
    val idList = this.prepareStatement(queryCreateIkkeAktuell).use {
        it.setString(1, ikkeAktuell.uuid.toString())
        it.setObject(2, ikkeAktuell.createdAt)
        it.setString(3, ikkeAktuell.createdBy)
        it.setString(4, ikkeAktuell.personIdent.value)
        it.setString(5, ikkeAktuell.arsak.name)
        it.setString(6, ikkeAktuell.beskrivelse)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating IKKEAKTUELL failed, no rows affected.")
    }
}
