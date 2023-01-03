package no.nav.syfo.identhendelse.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.PreparedStatement

const val queryUpdateKandidatStoppunkt =
    """
        UPDATE DIALOGMOTEKANDIDAT_STOPPUNKT
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updateKandidatStoppunkt(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(queryUpdateKandidatStoppunkt).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryUpdateKandidatEndring =
    """
        UPDATE DIALOGMOTEKANDIDAT_ENDRING
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updateKandidatEndring(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(queryUpdateKandidatEndring).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryUpdateUnntak =
    """
        UPDATE UNNTAK
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updateUnntak(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(queryUpdateUnntak).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryUpdateDialogmoteStatus =
    """
        UPDATE DIALOGMOTESTATUS
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updateDialogmoteStatus(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(queryUpdateDialogmoteStatus).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryGetIdentCount =
    """
        SELECT COUNT(*)
        FROM (
            SELECT personident FROM DIALOGMOTESTATUS
            UNION ALL
            SELECT personident FROM DIALOGMOTEKANDIDAT_STOPPUNKT
            UNION ALL
            SELECT personident FROM DIALOGMOTEKANDIDAT_ENDRING
            UNION ALL
            SELECT personident FROM UNNTAK
        ) identer
        WHERE personident = ?
    """

fun DatabaseInterface.getIdentCount(
    identer: List<PersonIdentNumber>,
): Int =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetIdentCount).use<PreparedStatement, Int> {
            var count = 0
            identer.forEach { ident ->
                it.setString(1, ident.value)
                count += it.executeQuery().toList { getInt(1) }.firstOrNull() ?: 0
            }
            return count
        }
    }
