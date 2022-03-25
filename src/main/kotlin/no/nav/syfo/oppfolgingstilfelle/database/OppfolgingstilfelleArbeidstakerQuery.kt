package no.nav.syfo.oppfolgingstilfelle.database

import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
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
    ON CONFLICT DO NOTHING
    RETURNING id
    """

fun Connection.createOppfolgingstilfelleArbeidstaker(
    commit: Boolean,
    oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker,
) {
    val idList = this.prepareStatement(queryCreateOppfolgingstilfelleArbeidstaker).use {
        it.setString(1, oppfolgingstilfelleArbeidstaker.uuid.toString())
        it.setObject(2, oppfolgingstilfelleArbeidstaker.createdAt)
        it.setString(3, oppfolgingstilfelleArbeidstaker.personIdent.value)
        it.setObject(4, oppfolgingstilfelleArbeidstaker.tilfelleGenerert)
        it.setDate(5, Date.valueOf(oppfolgingstilfelleArbeidstaker.tilfelleStart))
        it.setDate(6, Date.valueOf(oppfolgingstilfelleArbeidstaker.tilfelleEnd))
        it.setString(7, oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString())
        it.setObject(8, oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet)
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

fun DatabaseInterface.getOppfolgingstilfelleArbeidstakerList(
    arbeidstakerPersonIdent: PersonIdentNumber,
): List<POppfolgingstilfelleArbeidstaker> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetOppfolgingstilfelleArbeidstaker).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList {
                toPOppfolgingstilfelleArbeidstaker()
            }
        }
    }

fun ResultSet.toPOppfolgingstilfelleArbeidstaker(): POppfolgingstilfelleArbeidstaker =
    POppfolgingstilfelleArbeidstaker(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        personIdent = PersonIdentNumber(getString("personident")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        tilfelleGenerert = getObject("tilfelle_generated_at", OffsetDateTime::class.java),
        tilfelleStart = getDate("tilfelle_start").toLocalDate(),
        tilfelleEnd = getDate("tilfelle_end").toLocalDate(),
        referanseTilfelleBitInntruffet = getObject("referanse_tilfelle_bit_inntruffet", OffsetDateTime::class.java),
        referanseTilfelleBitUUID = UUID.fromString(getString("referanse_tilfelle_bit_uuid")),
    )
