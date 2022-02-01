package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

const val DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS = 112L

data class OppfolgingstilfelleArbeidstaker(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val tilfelleGenerert: OffsetDateTime,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

fun OppfolgingstilfelleArbeidstaker.isDialogmotekandidat(): Boolean {
    return tilfelleStart.until(tilfelleEnd, ChronoUnit.DAYS) >= DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
}
