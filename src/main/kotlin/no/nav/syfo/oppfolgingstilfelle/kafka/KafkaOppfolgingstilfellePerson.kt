package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.nowUTC
import no.nav.syfo.util.tomorrow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

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

fun KafkaOppfolgingstilfellePerson.toOppfolgingstilfelleArbeidstaker(
    latestTilfelle: KafkaOppfolgingstilfelle,
) = OppfolgingstilfelleArbeidstaker(
    uuid = UUID.fromString(this.uuid),
    createdAt = nowUTC(),
    personIdent = PersonIdentNumber(this.personIdentNumber),
    tilfelleGenerert = this.createdAt,
    tilfelleStart = latestTilfelle.start,
    tilfelleEnd = latestTilfelle.end,
    referanseTilfelleBitUuid = UUID.fromString(this.referanseTilfelleBitUuid),
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
)

fun KafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelleArbeidstaker(): OppfolgingstilfelleArbeidstaker? =
    this.oppfolgingstilfelleList.filter {
        it.start.isBefore(tomorrow())
    }.maxByOrNull {
        it.start
    }?.let { latestKafkaOppfolgingstilfelle ->
        if (latestKafkaOppfolgingstilfelle.arbeidstakerAtTilfelleEnd) {
            this.toOppfolgingstilfelleArbeidstaker(
                latestTilfelle = latestKafkaOppfolgingstilfelle
            )
        } else {
            null
        }
    }

fun KafkaOppfolgingstilfellePerson.toOppfolgingstilfelleArbeidstakerList() =
    this.oppfolgingstilfelleList.filter { kafkaOppfolgingstilfelle ->
        kafkaOppfolgingstilfelle.arbeidstakerAtTilfelleEnd && kafkaOppfolgingstilfelle.start.isBefore(tomorrow())
    }.map { oppfolgingstilfelle ->
        this.toOppfolgingstilfelleArbeidstaker(
            latestTilfelle = oppfolgingstilfelle,
        )
    }
