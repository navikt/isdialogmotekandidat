package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.util.*

fun generateOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdent: PersonIdentNumber,
    oppfolgingstilfelleDurationInDays: Long,
): OppfolgingstilfelleArbeidstaker = OppfolgingstilfelleArbeidstaker(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    personIdent = arbeidstakerPersonIdent,
    tilfelleGenerert = nowUTC().minusDays(1),
    tilfelleStart = LocalDate.now().minusDays(oppfolgingstilfelleDurationInDays),
    tilfelleEnd = LocalDate.now(),
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = nowUTC()
        .minusDays(oppfolgingstilfelleDurationInDays)
)
