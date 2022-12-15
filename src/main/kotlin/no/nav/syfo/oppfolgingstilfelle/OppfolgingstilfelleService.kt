package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.oppfolgingstilfelle.toOppfolgingstilfelle
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import java.time.LocalDate

class OppfolgingstilfelleService(
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
) {
    suspend fun getLatestOppfolgingstilfelle(
        arbeidstakerPersonIdent: PersonIdentNumber
    ) = getAllOppfolgingstilfeller(arbeidstakerPersonIdent).firstOrNull()

    suspend fun getOppfolgingstilfelleForDate(
        arbeidstakerPersonIdent: PersonIdentNumber,
        date: LocalDate,
    ) = getAllOppfolgingstilfeller(arbeidstakerPersonIdent)
        .firstOrNull { it.tilfelleStart.isBeforeOrEqual(date) && it.tilfelleEnd.isAfterOrEqual(date) }

    private suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: PersonIdentNumber
    ) = oppfolgingstilfelleClient.getOppfolgingstilfellePerson(personIdent = arbeidstakerPersonIdent)
        ?.oppfolgingstilfelleList?.filter { it.start.isBeforeOrEqual(tomorrow()) }
        ?.map { it.toOppfolgingstilfelle(arbeidstakerPersonIdent) }
        ?: emptyList()
}
