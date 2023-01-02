package no.nav.syfo.identhendelse.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.PreparedStatement

const val queryUpdateKandidatStoppunkt =
    """
        UPDATE DIALOGMOTEKANDIDAT_STOPPUNKT
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateKandidatStoppunkt(nyPersonident: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateKandidatStoppunkt).use {
            inactiveIdenter.forEach { inactiveIdent ->
                it.setString(1, nyPersonident.value)
                it.setString(2, inactiveIdent.value)
                updatedRows += it.executeUpdate()
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

fun DatabaseInterface.updateKandidatEndring(nyPersonident: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateKandidatEndring).use {
            inactiveIdenter.forEach { inactiveIdent ->
                it.setString(1, nyPersonident.value)
                it.setString(2, inactiveIdent.value)
                updatedRows += it.executeUpdate()
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

fun DatabaseInterface.updateUnntak(nyPersonident: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateUnntak).use {
            inactiveIdenter.forEach { inactiveIdent ->
                it.setString(1, nyPersonident.value)
                it.setString(2, inactiveIdent.value)
                updatedRows += it.executeUpdate()
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryUpdateDialogmoteStatus =
    """
        UPDATE DIALOGMOTESTATUS
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateDialogmoteStatus(nyPersonident: PersonIdentNumber, oldIdentList: List<PersonIdentNumber>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateDialogmoteStatus).use {
            oldIdentList.forEach { oldPersonident ->
                it.setString(1, nyPersonident.value)
                it.setString(2, oldPersonident.value)
                updatedRows += it.executeUpdate()
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryFindIdentOccurence =
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

fun DatabaseInterface.getIdentOccurenceCount(
    identer: List<PersonIdentNumber>,
): Int =
    this.connection.use { connection ->
        connection.prepareStatement(queryFindIdentOccurence).use<PreparedStatement, Int> {
            var occurences = 0
            identer.forEach { ident ->
                it.setString(1, ident.value)
                occurences += it.executeQuery().toList { getInt(1) }.firstOrNull() ?: 0
            }
            return occurences
        }
    }
