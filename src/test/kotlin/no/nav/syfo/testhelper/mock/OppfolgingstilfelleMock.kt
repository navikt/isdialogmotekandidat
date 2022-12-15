package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.dialogmotekandidat.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.personIdentHeader
import java.time.LocalDate

class OppfolgingstilfelleMock : MockServer() {
    override val name = "oppfolgingstilfelle"
    override val routingConfiguration: Routing.() -> Unit = {
        get(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH) {
            call.respond(
                oppfolgingstilfellePersonDTO(personIdentHeader())
            )
        }
        get(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_VEILEDER_PERSON_PATH) {
            call.respond(
                oppfolgingstilfellePersonDTO(personIdentHeader())
            )
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
                            virksomhetsnummerList = listOf(VIRKSOMHETSNUMMER_DEFAULT.value)
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
        )
}
