package no.nav.syfo.dialogmotekandidat.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatRepository(private val database: DatabaseInterface) {

    fun getDialogmotekandidatEndring(uuid: UUID): PDialogmotekandidatEndring =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEENDRING_QUERY).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPDialogmotekandidatEndringList() }
            }
        }.first()

    companion object {
        private const val GET_DIALOGMOTEENDRING_QUERY =
            """
                SELECT * 
                FROM DIALOGMOTEKANDIDAT_ENDRING
                WHERE uuid = ?
            """
    }
}

fun ResultSet.toPDialogmotekandidatEndringList() =
    PDialogmotekandidatEndring(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personIdent = PersonIdentNumber(getString("personident")),
        kandidat = getBoolean("kandidat"),
        arsak = getString("arsak"),
    )
