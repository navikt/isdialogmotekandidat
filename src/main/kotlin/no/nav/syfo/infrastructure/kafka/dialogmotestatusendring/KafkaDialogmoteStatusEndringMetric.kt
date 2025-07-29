package no.nav.syfo.infrastructure.kafka.dialogmotestatusendring

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE = "${METRICS_NS}_kafka_consumer_dialogmote_statusendring"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_READ = "${KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE}_read"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_TOMBSTONE =
    "${KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE}_tombstone"

const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_CREATED_KANDIDATENDRING =
    "${KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE}_created_kandidat_endring"

const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_RELEVANT =
    "${KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE}_skipped_not_relevant"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_KANDIDATENDRING =
    "${KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_BASE}_skipped_not_kandidat_endring"

val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_READ: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_READ)
        .description("Counts the number of reads from topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_TOMBSTONE: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_TOMBSTONE)
        .description("Counts the number of tombstones received from topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_CREATED_KANDIDATENDRING: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_CREATED_KANDIDATENDRING)
        .description("Counts the number of dialogmotekandidat-endring created from reading topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_RELEVANT: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_RELEVANT)
        .description("Counts the number of skipped from topic dialogmote-statusendring - not relevant")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_KANDIDATENDRING: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_KANDIDATENDRING)
        .description("Counts the number of skipped from topic dialogmote-statusendring - not dialogmotekandidat-endring")
        .register(METRICS_REGISTRY)
