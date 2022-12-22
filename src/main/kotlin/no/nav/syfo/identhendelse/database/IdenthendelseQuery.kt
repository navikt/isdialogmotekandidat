package no.nav.syfo.identhendelse.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatStoppunkt
import no.nav.syfo.dialogmotestatusendring.database.PDialogmoteStatus
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.database.domain.PUnntak
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

const val queryUpdateKandidatStoppunkt =
    """
        UPDATE DIALOGMOTEKANDIDAT_STOPPUNKT
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateKandidatStoppunkt(nyPersonident: PersonIdentNumber, stoppunktWithOldIdent: List<PDialogmotekandidatStoppunkt>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateKandidatStoppunkt).use {
            stoppunktWithOldIdent.forEach { stoppunkt ->
                it.setString(1, nyPersonident.value)
                it.setString(2, stoppunkt.personIdent.value)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryUpdateKandidatEndring =
    """
        UPDATE DIALOGMOTEKANDIDAT_ENDRING
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateKandidatEndring(nyPersonident: PersonIdentNumber, endringWithOldIdent: List<PDialogmotekandidatEndring>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateKandidatEndring).use {
            endringWithOldIdent.forEach { endring ->
                it.setString(1, nyPersonident.value)
                it.setString(2, endring.personIdent.value)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryUpdateUnntak =
    """
        UPDATE UNNTAK
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateUnntak(nyPersonident: PersonIdentNumber, unntakWithOldIdent: List<PUnntak>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateUnntak).use {
            unntakWithOldIdent.forEach { unntak ->
                it.setString(1, nyPersonident.value)
                it.setString(2, unntak.personIdent)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryGetDialogmoteStatus =
    """
        SELECT *
        FROM DIALOGMOTESTATUS
        WHERE personident = ?
    """

fun DatabaseInterface.getDialogmoteStatusList(
    personIdent: PersonIdentNumber,
): List<PDialogmoteStatus> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteStatus).use {
            it.setString(1, personIdent.value)
            it.executeQuery().toList { toPDialogmoteStatus() }
        }
    }

const val queryUpdateDialogmoteStatus =
    """
        UPDATE DIALOGMOTESTATUS
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateDialogmoteStatus(nyPersonident: PersonIdentNumber, dialogmoteStatusWithOldIdent: List<PDialogmoteStatus>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateDialogmoteStatus).use {
            dialogmoteStatusWithOldIdent.forEach { dialogmoteStatus ->
                it.setString(1, nyPersonident.value)
                it.setString(2, dialogmoteStatus.personIdent.value)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}

fun ResultSet.toPDialogmoteStatus(): PDialogmoteStatus {
    return PDialogmoteStatus(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personIdent = PersonIdentNumber(getString("personident")),
        moteTidspunkt = getObject("mote_tidspunkt", OffsetDateTime::class.java),
        ferdigstiltTidspunkt = getObject("ferdigstilt_tidspunkt", OffsetDateTime::class.java),
    )
}
