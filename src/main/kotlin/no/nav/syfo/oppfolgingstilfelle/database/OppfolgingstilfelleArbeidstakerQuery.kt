package no.nav.syfo.oppfolgingstilfelle.database

import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.sql.Date
import java.util.*

const val queryCreateOppfolgingstilfelleArbeidstaker =
    """
    INSERT INTO OPPFOLGINGSTILFELLE_ARBEIDSTAKER (
        id,
        uuid,
        created_at,
        personident,
        tilfelle_generated_at,
        tilfelle_start,
        tilfelle_end,
        referanse_tilfelle_bit_uuid,
        referanse_tilfelle_bit_inntruffet
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createOppfolgingstilfelleArbeidstaker(
    commit: Boolean,
    oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker,
) {
    val idList = this.prepareStatement(queryCreateOppfolgingstilfelleArbeidstaker).use {
        it.setString(1, oppfolgingstilfelleArbeidstaker.uuid.toString())
        it.setTimestamp(2, Timestamp.from(oppfolgingstilfelleArbeidstaker.createdAt.toInstant()))
        it.setString(3, oppfolgingstilfelleArbeidstaker.personIdent.value)
        it.setTimestamp(4, Timestamp.from(oppfolgingstilfelleArbeidstaker.tilfelleGenerert.toInstant()))
        it.setDate(5, Date.valueOf(oppfolgingstilfelleArbeidstaker.tilfelleStart))
        it.setDate(6, Date.valueOf(oppfolgingstilfelleArbeidstaker.tilfelleEnd))
        it.setString(7, oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString())
        it.setTimestamp(8, Timestamp.from(oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet.toInstant()))
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating OPPFOLGINGSTILFELLE_ARBEIDSTAKER failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }
}

const val queryGetOppfolgingstilfelleArbeidstaker =
    """
        SELECT * 
        FROM OPPFOLGINGSTILFELLE_ARBEIDSTAKER
        WHERE personident = ?
        ORDER BY referanse_tilfelle_bit_inntruffet DESC;
    """

fun DatabaseInterface.getOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdent: PersonIdentNumber,
): POppfolgingstilfelleArbeidstaker? {
    return this.connection.use {
        connection.prepareStatement(queryGetOppfolgingstilfelleArbeidstaker).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList { toPOppfolgingstilfelleArbeidstaker() }
        }
    }.firstOrNull()
}

fun ResultSet.toPOppfolgingstilfelleArbeidstaker(): POppfolgingstilfelleArbeidstaker =
    POppfolgingstilfelleArbeidstaker(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        personIdent = PersonIdentNumber(getString("personident")),
        createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
        tilfelleGenerert = getTimestamp("tilfelle_generated_at").toOffsetDateTimeUTC(),
        tilfelleStart = getDate("tilfelle_start").toLocalDate(),
        tilfelleEnd = getDate("tilfelle_end").toLocalDate(),
        referanseTilfelleBitInntruffet = getTimestamp("referanse_tilfelle_bit_inntruffet").toOffsetDateTimeUTC(),
        referanseTilfelleBitUUID = UUID.fromString(getString("referanse_tilfelle_bit_uuid")),
    )
