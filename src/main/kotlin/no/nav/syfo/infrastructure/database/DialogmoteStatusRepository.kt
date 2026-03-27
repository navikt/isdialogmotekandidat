package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.ITransaction
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.Personident
import java.time.OffsetDateTime
import java.util.*

class DialogmoteStatusRepository(private val database: DatabaseInterface) {

    fun createDialogmoteStatus(
        transaction: ITransaction,
        dialogmoteStatusEndring: DialogmoteStatusEndring,
    ) {
        val idList = transaction.connection.prepareStatement(CREATE_DIALOGMOTESTATUS_QUERY).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setObject(2, dialogmoteStatusEndring.createdAt)
            it.setString(3, dialogmoteStatusEndring.personIdentNumber.value)
            it.setObject(4, dialogmoteStatusEndring.moteTidspunkt)
            it.setObject(5, dialogmoteStatusEndring.statusTidspunkt)
            it.setString(6, dialogmoteStatusEndring.type.name)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw NoElementInsertedException("Creating DIALOGMOTESTATUS failed, no rows affected.")
        }
    }

    fun getLatestDialogmoteFerdigstiltForPerson(
        transaction: ITransaction,
        personident: Personident,
    ): OffsetDateTime? =
        transaction.connection.prepareStatement(GET_LATEST_DIALOGMOTE_FERDIGSTILT_QUERY).use {
            it.setString(1, personident.value)
            it.executeQuery().toList {
                getObject(1, OffsetDateTime::class.java)
            }
        }.firstOrNull()

    companion object {
        private const val CREATE_DIALOGMOTESTATUS_QUERY =
            """
            INSERT INTO DIALOGMOTESTATUS (
                id,
                uuid,
                created_at,
                personident,
                mote_tidspunkt,
                status_tidspunkt,
                status_type
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """

        private const val GET_LATEST_DIALOGMOTE_FERDIGSTILT_QUERY =
            """
            SELECT status_tidspunkt 
            FROM DIALOGMOTESTATUS
            WHERE personident = ? AND status_type = 'FERDIGSTILT'
            ORDER BY status_tidspunkt DESC;
            """
    }
}
