package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.IDialogmotekandidatVurderingRepository
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatVurderingRepository(private val database: DatabaseInterface) : IDialogmotekandidatVurderingRepository {

    override suspend fun getIkkeAktuellListForPerson(personIdent: Personident): List<PIkkeAktuell> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_IKKE_AKTUELL_FOR_PERSON).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPIkkeAktuell() }
            }
        }

    override suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: IkkeAktuell) {
        val idList = connection.prepareStatement(QUERY_CREATE_IKKE_AKTUELL).use {
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
        if (commit) connection.commit()
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
    }
}

private fun ResultSet.toPIkkeAktuell() = PIkkeAktuell(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    createdBy = getString("created_by"),
    personIdent = getString("personident"),
    arsak = getString("arsak"),
    beskrivelse = getString("beskrivelse"),
)
