package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

const val queryCreateUnntak =
    """
    INSERT INTO UNNTAK (
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

fun Connection.createUnntak(unntak: Unntak) {
    val idList = this.prepareStatement(queryCreateUnntak).use {
        it.setString(1, unntak.uuid.toString())
        it.setObject(2, unntak.createdAt)
        it.setString(3, unntak.createdBy)
        it.setString(4, unntak.personIdent.value)
        it.setString(5, unntak.arsak.name)
        it.setString(6, unntak.beskrivelse)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating UNNTAK failed, no rows affected.")
    }
}

const val queryGetUnntakForPerson: String =
    """
        SELECT * 
        FROM UNNTAK
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

fun DatabaseInterface.getUnntakList(
    personIdent: Personident,
): List<PUnntak> = this.connection.use { connection ->
    connection.prepareStatement(queryGetUnntakForPerson).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPUnntakList() }
    }
}

fun ResultSet.toPUnntakList() = PUnntak(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    createdBy = getString("created_by"),
    personIdent = getString("personident"),
    arsak = getString("arsak"),
    beskrivelse = getString("beskrivelse"),
)
