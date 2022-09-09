package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.util.*

fun generateOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdent: PersonIdentNumber,
    oppfolgingstilfelleDurationInDays: Long,
    backdatedNumberOfDays: Long = 0,
): OppfolgingstilfelleArbeidstaker = OppfolgingstilfelleArbeidstaker(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    personIdent = arbeidstakerPersonIdent,
    tilfelleGenerert = nowUTC().minusDays(1),
    tilfelleStart = LocalDate.now().minusDays(oppfolgingstilfelleDurationInDays + backdatedNumberOfDays),
    tilfelleEnd = LocalDate.now().minusDays(backdatedNumberOfDays),
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = nowUTC()
        .minusDays(oppfolgingstilfelleDurationInDays)
)
