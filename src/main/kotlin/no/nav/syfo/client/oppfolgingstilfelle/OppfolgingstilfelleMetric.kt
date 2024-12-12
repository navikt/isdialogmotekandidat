package no.nav.syfo.client.oppfolgingstilfelle

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_OPPFOLGINGSTILFELLE_BASE = "${METRICS_NS}_call_oppfolgingstilfelle"

const val CALL_OPPFOLGINGSTILFELLE_PERSON_BASE = "${CALL_OPPFOLGINGSTILFELLE_BASE}_person"
const val CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS = "${CALL_OPPFOLGINGSTILFELLE_PERSON_BASE}_success_count"
const val CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL = "${CALL_OPPFOLGINGSTILFELLE_PERSON_BASE}_fail_count"

val COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS: Counter = Counter
    .builder(CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS)
    .description("Counts the number of successful calls to Isoppfolgingstilfelle - OppfolgingstilfellePerson")
    .register(METRICS_REGISTRY)
val COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL: Counter = Counter
    .builder(CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL)
    .description("Counts the number of failed calls to Isoppfolgingstilfelle - OppfolgingstilfellePerson")
    .register(METRICS_REGISTRY)
