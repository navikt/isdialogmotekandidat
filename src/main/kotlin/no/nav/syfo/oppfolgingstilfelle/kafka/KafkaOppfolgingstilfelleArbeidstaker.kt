package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class KafkaOppfolgingstilfelleArbeidstaker(
    val uuid: String,
    val createdAt: OffsetDateTime,
    val personIdentNumber: String,
    val oppfolgingstilfelleList: List<KafkaOppfolgingstilfelle>,
    val referanseTilfelleBitUuid: String,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

data class KafkaOppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun KafkaOppfolgingstilfelleArbeidstaker.toOppfolgingstilfelleArbeidstaker(): OppfolgingstilfelleArbeidstaker {
    // TODO: Evt sende inn latestTilfelle hit og håndtere tom liste i service (log warn)
    val latestTilfelle =
        this.oppfolgingstilfelleList.maxByOrNull { it.start } ?: throw RuntimeException("No Oppfolgingstilfelle found")
    return OppfolgingstilfelleArbeidstaker(
        uuid = UUID.fromString(this.uuid),
        createdAt = OffsetDateTime.now(),
        personIdent = PersonIdentNumber(this.personIdentNumber),
        tilfelleGenerert = this.createdAt,
        tilfelleStart = latestTilfelle.start,
        tilfelleEnd = latestTilfelle.end,
        referanseTilfelleBitUuid = UUID.fromString(this.referanseTilfelleBitUuid),
        referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet
    )
}
