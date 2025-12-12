package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatRepository(private val database: DatabaseInterface) {

    fun getDialogmotekandidatEndring(uuid: UUID): PDialogmotekandidatEndring? =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEENDRING_QUERY).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPDialogmotekandidatEndringList() }
            }
        }.firstOrNull()

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
        personident = Personident(getString("personident")),
        kandidat = getBoolean("kandidat"),
        arsak = getString("arsak"),
    )
