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
        callId: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
        callId = callId,
    ).firstOrNull()

    suspend fun getOppfolgingstilfelleForDate(
        arbeidstakerPersonIdent: PersonIdentNumber,
        date: LocalDate,
        veilederToken: String? = null,
        callId: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
        callId = callId,
    ).firstOrNull { it.tilfelleStart.isBeforeOrEqual(date) && it.tilfelleEnd.isAfterOrEqual(date) }

    private suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: PersonIdentNumber,
        veilederToken: String? = null,
        callId: String? = null,
    ) = oppfolgingstilfelleClient.getOppfolgingstilfellePerson(
        personIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
        callId = callId,
    )
        ?.oppfolgingstilfelleList?.filter { it.start.isBeforeOrEqual(tomorrow()) }
        ?.map { it.toOppfolgingstilfelle(arbeidstakerPersonIdent) }
        ?: emptyList()
}
