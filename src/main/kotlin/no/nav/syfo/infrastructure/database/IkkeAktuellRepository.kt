package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.PersonIdentNumber
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class IkkeAktuellRepository(private val database: DatabaseInterface) {

    fun getIkkeAktuellListForPerson(personIdent: PersonIdentNumber): List<PIkkeAktuell> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_IKKE_AKTUELL_FOR_PERSON).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPIkkeAktuell() }
            }
        }

    companion object {
        private const val GET_IKKE_AKTUELL_FOR_PERSON =
            """
                    SELECT * 
                    FROM IKKE_AKTUELL
                    WHERE personident = ?
                """
    }
}

fun ResultSet.toPIkkeAktuell() = PIkkeAktuell(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    createdBy = getString("created_by"),
    personIdent = getString("personident"),
    arsak = getString("arsak"),
    beskrivelse = getString("beskrivelse"),
)
