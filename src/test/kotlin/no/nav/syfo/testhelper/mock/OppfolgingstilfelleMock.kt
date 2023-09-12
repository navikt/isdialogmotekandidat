package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSONS_PATH
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.dialogmotekandidat.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import java.time.LocalDate

suspend fun MockRequestHandleScope.oppfolgingstilfelleMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    return if (requestUrl.endsWith(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSONS_PATH)) {
        val personidenter = request.receiveBody<List<String>>()
        val oppfolgingstilfellePersonDTOS = personidenter
            .filter { it != PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value }
            .map { oppfolgingstilfellePersonDTO(it) }
        respond(oppfolgingstilfellePersonDTOS)
    } else {
        val personIdent = request.headers[NAV_PERSONIDENT_HEADER]
        respond(oppfolgingstilfellePersonDTO(personIdent))
    }
}

private fun oppfolgingstilfellePersonDTO(personIdent: String?) =
    OppfolgingstilfellePersonDTO(
        oppfolgingstilfelleList = when (personIdent) {
            UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER.value -> {
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
                        end = LocalDate.now(),
                        virksomhetsnummerList = listOf(VIRKSOMHETSNUMMER_DEFAULT.value),
                    )
                )
            }

            UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT.value -> {
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1),
                        end = LocalDate.now(),
                        virksomhetsnummerList = listOf(VIRKSOMHETSNUMMER_DEFAULT.value)
                    )
                )
            }

            UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT.value -> {
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1),
                        end = LocalDate.now().minusDays(1),
                        virksomhetsnummerList = listOf(VIRKSOMHETSNUMMER_DEFAULT.value)
                    )
                )
            }

            else -> {
                emptyList()
            }
        },
        personIdent = personIdent!!,
        dodsdato = if (personIdent == UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD.value) LocalDate.now() else null,
    )
