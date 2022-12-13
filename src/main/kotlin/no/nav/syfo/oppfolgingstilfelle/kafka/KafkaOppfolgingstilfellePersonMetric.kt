package no.nav.syfo.oppfolgingstilfelle.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE = "${METRICS_NS}_kafka_consumer_oppfolgingstilfelle_person"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE}_read"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE}_tombstone"

const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE}_skipped_no_tilfelle"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE}_skipped_not_kandidat"
const val KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_PLANLAGT_KANDIDAT = "${KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_BASE}_kandidat"

val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ)
    .description("Counts the number of reads from topic - oppfolgingstilfelle-person")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE)
    .description("Counts the number of tombstones received from topic - oppfolgingstilfelle-person")
    .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE)
    .description("Counts the number of skipped from topic - oppfolgingstilfelle-person - Person has empty tilfelleList")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT)
    .description("Counts the number of skipped from topic - oppfolgingstilfelle-person - Person is not Dialogmotekandidat")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_PLANLAGT_KANDIDAT: Counter = Counter.builder(KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_PLANLAGT_KANDIDAT)
    .description("Counts the number of skipped from topic - oppfolgingstilfelle-person - Person is Dialogmotekandidat")
    .register(METRICS_REGISTRY)
