package no.nav.syfo.application

import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.tilfelleForDate
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.toOppfolgingstilfelleList
import java.time.LocalDate

class OppfolgingstilfelleService(
    private val oppfolgingstilfelleClient: IOppfolgingstilfelleClient,
) {
    suspend fun getLatestOppfolgingstilfelle(
        arbeidstakerPersonIdent: Personident,
        veilederToken: String? = null,
        callId: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
        callId = callId,
    ).firstOrNull()

    suspend fun getOppfolgingstilfelleForDate(
        arbeidstakerPersonIdent: Personident,
        date: LocalDate,
        veilederToken: String? = null,
        callId: String? = null,
    ) = getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        veilederToken = veilederToken,
        callId = callId,
    ).tilfelleForDate(date)

    private suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: Personident,
        veilederToken: String? = null,
        callId: String? = null,
    ): List<Oppfolgingstilfelle> {
        val oppfolgingstilfellePerson = oppfolgingstilfelleClient.getOppfolgingstilfellePerson(
            personident = arbeidstakerPersonIdent,
            veilederToken = veilederToken,
            callId = callId,
        )

        return oppfolgingstilfellePerson.toOppfolgingstilfelleList(arbeidstakerPersonIdent)
    }
}
