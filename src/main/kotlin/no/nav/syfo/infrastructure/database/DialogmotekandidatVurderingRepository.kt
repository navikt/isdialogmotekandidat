package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.IDialogmotekandidatVurderingRepository
import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

class DialogmotekandidatVurderingRepository(private val database: DatabaseInterface) : IDialogmotekandidatVurderingRepository {

    override suspend fun getIkkeAktuellListForPerson(personident: Personident): List<PIkkeAktuell> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_IKKE_AKTUELL_FOR_PERSON).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPIkkeAktuell() }
            }
        }

    override suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: IkkeAktuell) {
        val idList = connection.prepareStatement(QUERY_CREATE_IKKE_AKTUELL).use {
            it.setString(1, ikkeAktuell.uuid.toString())
            it.setObject(2, ikkeAktuell.createdAt)
            it.setString(3, ikkeAktuell.createdBy)
            it.setString(4, ikkeAktuell.personident.value)
            it.setString(5, ikkeAktuell.arsak.name)
            it.setString(6, ikkeAktuell.beskrivelse)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating IKKEAKTUELL failed, no rows affected.")
        }
        if (commit) connection.commit()
    }

    override suspend fun createUnntak(connection: Connection, unntak: Unntak) {
        val idList = connection.prepareStatement(QUERY_CREATE_UNNTAK).use {
            it.setString(1, unntak.uuid.toString())
            it.setObject(2, unntak.createdAt)
            it.setString(3, unntak.createdBy)
            it.setString(4, unntak.personident.value)
            it.setString(5, unntak.arsak.name)
            it.setString(6, unntak.beskrivelse)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating UNNTAK failed, no rows affected.")
        }
    }

    override suspend fun createAvvent(connection: Connection, avvent: Avvent) {
        val idList = connection.prepareStatement(QUERY_CREATE_AVVENT).use {
            it.setString(1, avvent.uuid.toString())
            it.setObject(2, avvent.createdAt)
            it.setDate(3, Date.valueOf(avvent.frist))
            it.setString(4, avvent.createdBy)
            it.setString(5, avvent.personident.value)
            it.setString(6, avvent.beskrivelse)
            it.setBoolean(7, avvent.isLukket)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating AVVENT failed, no rows affected.")
        }
    }

    override suspend fun lukkAvvent(connection: Connection, avvent: Avvent) {
        val updated = connection.prepareStatement(QUERY_LUKK_AVVENT).use {
            it.setString(1, avvent.uuid.toString())
            it.executeUpdate()
        }

        if (updated != 1) {
            throw NoElementInsertedException("Updating AVVENT failed, no rows affected.")
        }
    }

    override suspend fun getUnntakList(personident: Personident): List<PUnntak> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_UNNTAK_FOR_PERSON).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPUnntakList() }
            }
        }

    override suspend fun getAvventList(personident: Personident): List<PAvvent> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_AVVENT_FOR_PERSON).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPAvventList() }
            }
        }

    companion object {
        private const val GET_IKKE_AKTUELL_FOR_PERSON =
            """
                SELECT * 
                FROM IKKE_AKTUELL
                WHERE personident = ?
            """

        private const val QUERY_CREATE_IKKE_AKTUELL =
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

        private const val QUERY_CREATE_UNNTAK =
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

        private const val QUERY_CREATE_AVVENT =
            """
                INSERT INTO AVVENT (
                    id,
                    uuid,
                    created_at,
                    frist,
                    created_by,
                    personident,
                    beskrivelse,
                    is_lukket
                ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
            """

        private const val QUERY_LUKK_AVVENT =
            """
                UPDATE AVVENT SET is_lukket=true WHERE uuid=?
            """

        private const val QUERY_GET_UNNTAK_FOR_PERSON: String =
            """
                SELECT * 
                FROM UNNTAK
                WHERE personident = ?
                ORDER BY created_at DESC;
            """

        private const val QUERY_GET_AVVENT_FOR_PERSON: String =
            """
                SELECT * 
                FROM AVVENT
                WHERE personident = ?
                ORDER BY created_at DESC;
            """
    }
}

private fun ResultSet.toPIkkeAktuell() =
    PIkkeAktuell(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        createdBy = getString("created_by"),
        personident = getString("personident"),
        arsak = getString("arsak"),
        beskrivelse = getString("beskrivelse"),
    )

fun ResultSet.toPUnntakList() =
    PUnntak(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        createdBy = getString("created_by"),
        personident = getString("personident"),
        arsak = getString("arsak"),
        beskrivelse = getString("beskrivelse"),
    )

fun ResultSet.toPAvventList() =
    PAvvent(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        frist = getDate("frist").toLocalDate(),
        createdBy = getString("created_by"),
        personident = getString("personident"),
        beskrivelse = getString("beskrivelse"),
        isLukket = getBoolean("is_lukket"),
    )
