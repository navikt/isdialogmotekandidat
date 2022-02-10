package no.nav.syfo.oppfolgingstilfelle.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_BASE = "${METRICS_NS}_kafka_consumer_oppfolgingstilfelle_at"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_READ = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_BASE}_read"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_CREATED = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_BASE}_created"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_DUPLICATE = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_BASE}_duplicate"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOMBSTONE = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_BASE}_tombstone"

val COUNT_KAFKA_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_READ: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_READ)
    .description("Counts the number of reads from topic - oppfolgingstilfelle-arbeidstaker")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_CREATED: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_CREATED)
    .description("Counts the number of created from topic - oppfolgingstilfelle-arbeidstaker")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_DUPLICATE: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_DUPLICATE)
    .description("Counts the number of duplicates received from topic - oppfolgingstilfelle-arbeidstaker")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOMBSTONE)
    .description("Counts the number of tombstones received from topic - oppfolgingstilfelle-arbeidstaker")
    .register(METRICS_REGISTRY)
