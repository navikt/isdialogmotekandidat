package no.nav.syfo.infrastructure.database.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toAvventList
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.infrastructure.database.toPAvventList
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class DialogmotekandidatRepository(private val database: DatabaseInterface) {

    fun getDialogmotekandidatEndring(uuid: UUID): PDialogmotekandidatEndring? =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEENDRING_QUERY).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPDialogmotekandidatEndringList() }
            }
        }.firstOrNull()

    fun getDialogmotekandidatEndringer(personident: Personident, connection: Connection? = null): List<DialogmotekandidatEndring> =
        connection?.getDialogmotekandidatEndringer(personident) ?: database.connection.use { connection ->
            connection.getDialogmotekandidatEndringer(personident)
        }

    private fun Connection.getDialogmotekandidatEndringer(personident: Personident): List<DialogmotekandidatEndring> =
        this.prepareStatement(GET_DIALOGMOTEENDRING_FOR_PERSON_QUERY).use {
            it.setString(1, personident.value)
            it.executeQuery()
                .toList { toPDialogmotekandidatEndringList() }
                .toDialogmotekandidatEndringList()
        }

    fun getDialogmotekandidatEndringForPersons(personidenter: List<Personident>): List<DialogmotekandidatEndring> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTEENDRING_FOR_PERSONS_QUERY).use {
                it.setString(1, personidenter.joinToString(transform = { personident -> personident.value }, separator = ","))
                it.executeQuery()
                    .toList { toPDialogmotekandidatEndringList() }
                    .toDialogmotekandidatEndringList()
            }
        }

    fun getAvventForPersons(personidenter: List<Personident>): List<DialogmotekandidatEndring.Avvent> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AVVENT_FOR_PERSONS).use {
                it.setString(1, personidenter.joinToString(transform = { personident -> personident.value }, separator = ","))
                it.executeQuery()
                    .toList { toPAvventList() }
                    .toAvventList()
            }
        }

    fun findOutdatedDialogmotekandidater(cutoff: LocalDateTime): List<DialogmotekandidatEndring> =
        database.connection.use { connection ->
            connection.prepareStatement(FIND_OUTDATED_DIALOGMOTEKANDIDATER_QUERY).use {
                it.setTimestamp(1, Timestamp.from(cutoff.toInstantOslo()))
                it.executeQuery().toList { toPDialogmotekandidatEndringList() }
            }
        }.toDialogmotekandidatEndringList()

    private fun LocalDateTime.toInstantOslo(): Instant =
        toInstant(ZoneId.of("Europe/Oslo").rules.getOffset(this))

    private fun ResultSet.toPDialogmotekandidatEndringList() =
        PDialogmotekandidatEndring(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
            personident = Personident(getString("personident")),
            kandidat = getBoolean("kandidat"),
            arsak = getString("arsak"),
        )

    companion object {
        private const val GET_DIALOGMOTEENDRING_QUERY =
            """
                SELECT * 
                FROM DIALOGMOTEKANDIDAT_ENDRING
                WHERE uuid = ?
            """

        private const val GET_DIALOGMOTEENDRING_FOR_PERSON_QUERY =
            """
                SELECT * 
                FROM DIALOGMOTEKANDIDAT_ENDRING
                WHERE personident = ?
                ORDER BY created_at DESC
            """

        private const val GET_DIALOGMOTEENDRING_FOR_PERSONS_QUERY =
            """
                SELECT *
                FROM DIALOGMOTEKANDIDAT_ENDRING
                WHERE kandidat AND personident = ANY (string_to_array(?, ','))
            """

        private const val GET_AVVENT_FOR_PERSONS: String =
            """
                SELECT *
                FROM AVVENT
                WHERE personident = ANY (string_to_array(?, ','))
            """

        private const val FIND_OUTDATED_DIALOGMOTEKANDIDATER_QUERY =
            """
                select * from dialogmotekandidat_endring d
                where d.created_at = (select max(d2.created_at) from dialogmotekandidat_endring d2 where d2.personident = d.personident)
                and d.created_at < ? and d.kandidat 
                LIMIT 200;
            """
    }
}
