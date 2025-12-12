package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import java.time.LocalDate

fun MockRequestHandleScope.oppfolgingstilfelleMockResponse(request: HttpRequestData): HttpResponseData {
    val personident = request.headers[NAV_PERSONIDENT_HEADER]
    return respond(oppfolgingstilfellePersonDTO(personident))
}

private fun oppfolgingstilfellePersonDTO(personident: String?) =
    OppfolgingstilfellePersonDTO(
        oppfolgingstilfelleList = when (personident) {
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
        personident = personident!!,
        dodsdato = if (personident == UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD.value) LocalDate.now() else null,
    )
