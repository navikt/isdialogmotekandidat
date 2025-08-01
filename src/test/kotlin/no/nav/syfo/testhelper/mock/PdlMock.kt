package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.pdl.domain.IdentType
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlHentIdenter
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlHentIdenterRequest
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlIdent
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlIdenter
import no.nav.syfo.infrastructure.clients.pdl.domain.PdlIdenterResponse
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
    return when (val personIdent = Personident(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER -> {
            respond(generatePdlIdenterResponse(Personident("11111111111")))
        }
        UserConstants.FEILENDE_PERSONIDENTNUMBER -> {
            respond(HttpStatusCode.InternalServerError)
        }
        else -> {
            respond(generatePdlIdenterResponse(personIdent))
        }
    }
}

private fun generatePdlIdenterResponse(
    personIdentNumber: Personident,
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

private fun Personident.toHistoricalPersonIdentNumber(): Personident {
    val firstDigit = this.value[0].digitToInt()
    val newDigit = firstDigit + 4
    val dNummer = this.value.replace(
        firstDigit.toString(),
        newDigit.toString(),
    )
    return Personident(dNummer)
}
