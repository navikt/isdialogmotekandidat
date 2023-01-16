package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.oppfolgingstilfelle.toOppfolgingstilfelle
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.tilfelleForDate
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
    ).tilfelleForDate(date)

    internal suspend fun getAllOppfolgingstilfeller(
        arbeidstakerPersonIdent: PersonIdentNumber,
        veilederToken: String? = null,
        callId: String? = null,
    ): List<Oppfolgingstilfelle> {

        val oppfolgingstilfellePerson = oppfolgingstilfelleClient.getOppfolgingstilfellePerson(
            personIdent = arbeidstakerPersonIdent,
            veilederToken = veilederToken,
            callId = callId,
        )
        return oppfolgingstilfellePerson?.oppfolgingstilfelleList?.filter { it.start.isBeforeOrEqual(tomorrow()) }
            ?.map {
                it.toOppfolgingstilfelle(
                    personIdent = arbeidstakerPersonIdent,
                    dodsdato = oppfolgingstilfellePerson.dodsdato,
                )
            } ?: emptyList()
    }
}
