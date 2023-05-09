package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_2_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT
import no.nav.syfo.testhelper.UserConstants.FEILENDE_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import no.nav.syfo.util.personIdentHeader

class SyfoTilgangskontrollMock : MockServer() {
    override val name = "syfotilgangskontroll"
    override val routingConfiguration: Routing.() -> Unit = {
        get(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) {
            when (personIdentHeader()) {
                PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value -> call.respond(
                    Tilgang(harTilgang = false)
                )

                else -> call.respond(
                    Tilgang(harTilgang = true)
                )
            }
        }
        post(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_LIST_PATH) {
            call.respond(
                listOf(
                    ARBEIDSTAKER_PERSONIDENTNUMBER.value,
                    ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT.value,
                    ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT.value,
                    ARBEIDSTAKER_PERSONIDENTNUMBER_DOD.value,
                    ARBEIDSTAKER_2_PERSONIDENTNUMBER.value,
                    ARBEIDSTAKER_3_PERSONIDENTNUMBER.value,
                    FEILENDE_PERSONIDENTNUMBER.value,
                )
            )
        }
    }
}
