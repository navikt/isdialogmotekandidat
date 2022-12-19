package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class KafkaOppfolgingstilfellePerson(
    val uuid: String,
    val createdAt: OffsetDateTime,
    val personIdentNumber: String,
    val oppfolgingstilfelleList: List<KafkaOppfolgingstilfelle>,
    val referanseTilfelleBitUuid: String,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

data class KafkaOppfolgingstilfelle(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun KafkaOppfolgingstilfellePerson.toOppfolgingstilfelle(
    tilfelle: KafkaOppfolgingstilfelle,
) = Oppfolgingstilfelle(
    personIdent = PersonIdentNumber(this.personIdentNumber),
    tilfelleStart = tilfelle.start,
    tilfelleEnd = tilfelle.end,
    arbeidstakerAtTilfelleEnd = tilfelle.arbeidstakerAtTilfelleEnd,
    virksomhetsnummerList = tilfelle.virksomhetsnummerList.map { Virksomhetsnummer(it) },
)

fun KafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelle(): Oppfolgingstilfelle? =
    this.oppfolgingstilfelleList.maxByOrNull {
        it.start
    }?.let { latestKafkaOppfolgingstilfelle ->
        if (latestKafkaOppfolgingstilfelle.arbeidstakerAtTilfelleEnd) {
            this.toOppfolgingstilfelle(
                tilfelle = latestKafkaOppfolgingstilfelle
            )
        } else {
            null
        }
    }

fun KafkaOppfolgingstilfellePerson.toCurrentOppfolgingstilfelle(): Oppfolgingstilfelle? {
    val today = LocalDate.now()
    return this.oppfolgingstilfelleList.firstOrNull {
        it.start.isBeforeOrEqual(today) && it.end.isAfterOrEqual(today)
    }?.let { currentKafkaOppfolgingstilfelle ->
        if (currentKafkaOppfolgingstilfelle.arbeidstakerAtTilfelleEnd) {
            this.toOppfolgingstilfelle(
                tilfelle = currentKafkaOppfolgingstilfelle
            )
        } else {
            null
        }
    }
}
