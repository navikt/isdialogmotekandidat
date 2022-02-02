package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.defaultZoneOffset
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdent: PersonIdentNumber,
    oppfolgingstilfelleDurationInDays: Long,
): OppfolgingstilfelleArbeidstaker = OppfolgingstilfelleArbeidstaker(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(defaultZoneOffset),
    personIdent = arbeidstakerPersonIdent,
    tilfelleGenerert = OffsetDateTime.now(defaultZoneOffset).minusDays(1),
    tilfelleStart = LocalDate.now().minusDays(oppfolgingstilfelleDurationInDays),
    tilfelleEnd = LocalDate.now(),
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = OffsetDateTime.now(defaultZoneOffset)
        .minusDays(oppfolgingstilfelleDurationInDays)
)
