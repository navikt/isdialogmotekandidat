package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.time.LocalDate

fun generateOppfolgingstilfelle(
    arbeidstakerPersonIdent: PersonIdentNumber,
    oppfolgingstilfelleDurationInDays: Long,
    backdatedNumberOfDays: Long = 0,
) = Oppfolgingstilfelle(
    personIdent = arbeidstakerPersonIdent,
    tilfelleStart = LocalDate.now().minusDays(oppfolgingstilfelleDurationInDays + backdatedNumberOfDays),
    tilfelleEnd = LocalDate.now().minusDays(backdatedNumberOfDays),
    arbeidstakerAtTilfelleEnd = true,
    virksomhetsnummerList = emptyList(),
    dodsdato = null,
)
