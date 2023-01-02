package no.nav.syfo.testhelper.mock

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants

class PdlMock : MockServer() {
    override val name = "pdl"
    override val routingConfiguration: Routing.() -> Unit = {
        post {
            val pdlRequest = call.receive<PdlHentIdenterRequest>()
            when (val personIdentNumber = PersonIdentNumber(pdlRequest.variables.ident)) {
                UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER -> {
                    call.respond(generatePdlIdenterResponse(PersonIdentNumber("11111111111")))
                }
                UserConstants.FEILENDE_PERSONIDENTNUMBER -> {
                    call.respond(HttpStatusCode.InternalServerError)
                }
                else -> {
                    call.respond(generatePdlIdenterResponse(personIdentNumber))
                }
            }
        }
    }

    private fun PersonIdentNumber.toHistoricalPersonIdentNumber(): PersonIdentNumber {
        val firstDigit = this.value[0].digitToInt()
        val newDigit = firstDigit + 4
        val dNummer = this.value.replace(
            firstDigit.toString(),
            newDigit.toString(),
        )
        return PersonIdentNumber(dNummer)
    }

    private fun generatePdlIdenterResponse(
        personIdentNumber: PersonIdentNumber,
    ) = PdlIdenterResponse(
        data = PdlHentIdenter(
            hentIdenter = PdlIdenter(
                identer = listOf(
                    PdlIdent(
                        ident = personIdentNumber.value,
                        historisk = false,
                        gruppe = IdentType.FOLKEREGISTERIDENT,
                    ),
                    PdlIdent(
                        ident = personIdentNumber.toHistoricalPersonIdentNumber().value,
                        historisk = true,
                        gruppe = IdentType.FOLKEREGISTERIDENT,
                    ),
                ),
            ),
        ),
        errors = null,
    )
}
