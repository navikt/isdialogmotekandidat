package no.nav.syfo.oppfolgingstilfelle.kafka

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class KafkaOppfolgingstilfelleArbeidstaker(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: String,
    val oppfolgingstilfelleList: List<KafkaOppfolgingstilfelle>,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

data class KafkaOppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)
