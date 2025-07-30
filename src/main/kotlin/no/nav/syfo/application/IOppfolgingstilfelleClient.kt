package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfellePersonDTO

interface IOppfolgingstilfelleClient {

    suspend fun getOppfolgingstilfellePerson(
        personIdent: Personident,
        veilederToken: String? = null,
        callId: String?,
    ): OppfolgingstilfellePersonDTO?
}
