package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.database.*
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate

class OppfolgingstilfelleService(
    private val database: DatabaseInterface,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val readFromIsoppfolgingstilfelleEnabled: Boolean,
) {
    suspend fun getLatestOppfolgingstilfelle(
        arbeidstakerPersonIdent: PersonIdentNumber
    ) = getAllOppfolgingstilfeller(arbeidstakerPersonIdent).firstOrNull()

    suspend fun getOppfolgingstilfelleForDate(
        arbeidstakerPersonIdent: PersonIdentNumber,
        date: LocalDate,
    ) = getAllOppfolgingstilfeller(arbeidstakerPersonIdent)
        .filter { it.start.isBeforeOrEqual(date) && it.end.isAfterOrEqual(date) }
        .firstOrNull()

    private suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: PersonIdentNumber
    ) = if (readFromIsoppfolgingstilfelleEnabled) {
        oppfolgingstilfelleClient.getOppfolgingstilfellePerson(personIdent = arbeidstakerPersonIdent)?.oppfolgingstilfelleList
            ?: emptyList()
    } else {
        database.getOppfolgingstilfelleArbeidstakerList(arbeidstakerPersonIdent = arbeidstakerPersonIdent)
            .map { it.toOppfolgingstilfelleDTO() }
    }
}
