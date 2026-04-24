package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.IDialogmotekandidatVurderingRepository
import no.nav.syfo.application.ITransaction
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatVurderingRepository(private val database: DatabaseInterface) : IDialogmotekandidatVurderingRepository {

    override fun getIkkeAktuellListForPerson(personident: Personident): List<DialogmotekandidatEndring.IkkeAktuell> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_IKKE_AKTUELL_FOR_PERSON).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPIkkeAktuell() }
            }
        }.toIkkeAktuellList()

    override fun createIkkeAktuell(transaction: ITransaction, ikkeAktuell: DialogmotekandidatEndring.IkkeAktuell) {
        val idList = transaction.connection.prepareStatement(QUERY_CREATE_IKKE_AKTUELL).use {
            it.setString(1, ikkeAktuell.uuid.toString())
            it.setObject(2, ikkeAktuell.createdAt)
            it.setString(3, ikkeAktuell.createdBy)
            it.setString(4, ikkeAktuell.personident.value)
            it.setString(5, ikkeAktuell.ikkeAktuellArsak.name)
            it.setString(6, ikkeAktuell.beskrivelse)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating IKKEAKTUELL failed, no rows affected.")
        }
    }

    override fun createUnntak(transaction: ITransaction, unntak: DialogmotekandidatEndring.Unntak) {
        val idList = transaction.connection.prepareStatement(QUERY_CREATE_UNNTAK).use {
            it.setString(1, unntak.uuid.toString())
            it.setObject(2, unntak.createdAt)
            it.setString(3, unntak.createdBy)
            it.setString(4, unntak.personident.value)
            it.setString(5, unntak.unntakArsak.name)
            it.setString(6, unntak.beskrivelse)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating UNNTAK failed, no rows affected.")
        }
    }

    override fun getUnntakList(personident: Personident): List<DialogmotekandidatEndring.Unntak> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_UNNTAK_FOR_PERSON).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPUnntakList() }
            }
        }.toUnntakList()

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

    private fun ResultSet.toPUnntakList() =
        PUnntak(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            createdBy = getString("created_by"),
            personident = getString("personident"),
            arsak = getString("arsak"),
            beskrivelse = getString("beskrivelse"),
        )

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

        private const val QUERY_GET_UNNTAK_FOR_PERSON: String =
            """
                SELECT * 
                FROM UNNTAK
                WHERE personident = ?
                ORDER BY created_at DESC;
            """
    }
}
