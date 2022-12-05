package no.nav.syfo.testhelper.mock

import io.ktor.server.routing.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.util.personIdentHeader

class OppfolgingstilfelleMock : MockServer() {
    override val name = "oppfolgingstilfelle"
    override val routingConfiguration: Routing.() -> Unit = {
        get {
            OppfolgingstilfellePersonDTO(
                oppfolgingstilfelleList = emptyList(),
                personIdent = personIdentHeader()!!,
            )
        }
    }
}
