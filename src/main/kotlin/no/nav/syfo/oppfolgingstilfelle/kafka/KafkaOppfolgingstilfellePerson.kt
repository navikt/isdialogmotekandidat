package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.tomorrow
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
    latestTilfelle: KafkaOppfolgingstilfelle,
) = Oppfolgingstilfelle(
    personIdent = PersonIdentNumber(this.personIdentNumber),
    tilfelleStart = latestTilfelle.start,
    tilfelleEnd = latestTilfelle.end,
    arbeidstakerAtTilfelleEnd = latestTilfelle.arbeidstakerAtTilfelleEnd,
    virksomhetsnummerList = latestTilfelle.virksomhetsnummerList.map { Virksomhetsnummer(it) },
)

fun KafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelle(): Oppfolgingstilfelle? =
    this.oppfolgingstilfellerWithoutFutureTilfeller().maxByOrNull {
        it.start
    }?.let { latestKafkaOppfolgingstilfelle ->
        if (latestKafkaOppfolgingstilfelle.arbeidstakerAtTilfelleEnd) {
            this.toOppfolgingstilfelle(
                latestTilfelle = latestKafkaOppfolgingstilfelle
            )
        } else {
            null
        }
    }

fun KafkaOppfolgingstilfellePerson.oppfolgingstilfellerWithoutFutureTilfeller() =
    this.oppfolgingstilfelleList.filter {
        it.start.isBefore(tomorrow())
    }
