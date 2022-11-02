package no.nav.syfo.unntak.database

import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.unntak.database.domain.PUnntak
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

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
    personIdent: PersonIdentNumber,
): List<PUnntak> = this.connection.use { connection ->
    connection.prepareStatement(queryGetUnntakForPerson).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPUnntakList() }
    }
}

const val queryGetUnntakForVeilder: String =
    """
        SELECT * 
        FROM UNNTAK
        WHERE created_by = ?
        ORDER BY created_at DESC;
    """

fun DatabaseInterface.getUnntakForVeileder(
    veilederIdent: String,
): List<PUnntak> = this.connection.use { connection ->
    connection.prepareStatement(queryGetUnntakForVeilder).use {
        it.setString(1, veilederIdent)
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
