package no.nav.syfo.identhendelse.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatStoppunkt
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.database.domain.PUnntak

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

fun DatabaseInterface.updateKandidatEndring(nyPersonident: PersonIdentNumber, endringWithOldIdent: List<PDialogmotekandidatEndring>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateKandidatEndring).use {
            endringWithOldIdent.forEach { endring ->
                it.setString(1, nyPersonident.value)
                it.setString(2, endring.personIdent.value)
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

fun DatabaseInterface.updateUnntak(nyPersonident: PersonIdentNumber, unntakWithOldIdent: List<PUnntak>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateUnntak).use {
            unntakWithOldIdent.forEach { unntak ->
                it.setString(1, nyPersonident.value)
                it.setString(2, unntak.personIdent)
                updatedRows += it.executeUpdate()
            }
        }
        connection.commit()
    }
    return updatedRows
}

const val queryGetDialogmoteStatusCount =
    """
        SELECT COUNT(*)
        FROM DIALOGMOTESTATUS
        WHERE personident = ?
    """

fun DatabaseInterface.getDialogmoteStatusCount(
    personIdent: PersonIdentNumber,
): Int =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteStatusCount).use {
            it.setString(1, personIdent.value)
            it.executeQuery().toList { getInt(1) }
        }
    }.firstOrNull() ?: 0

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
