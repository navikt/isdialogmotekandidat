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
        arbeidstakerPersonIdent: PersonIdentNumber,
        veilederToken: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
    ).firstOrNull()

    suspend fun getOppfolgingstilfelleForDate(
        arbeidstakerPersonIdent: PersonIdentNumber,
        date: LocalDate,
        veilederToken: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
    ).firstOrNull { it.tilfelleStart.isBeforeOrEqual(date) && it.tilfelleEnd.isAfterOrEqual(date) }

    private suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: PersonIdentNumber,
        veilederToken: String? = null,
    ) = oppfolgingstilfelleClient.getOppfolgingstilfellePerson(
        personIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
    )
        ?.oppfolgingstilfelleList?.filter { it.start.isBeforeOrEqual(tomorrow()) }
        ?.map { it.toOppfolgingstilfelle(arbeidstakerPersonIdent) }
        ?: emptyList()
}
