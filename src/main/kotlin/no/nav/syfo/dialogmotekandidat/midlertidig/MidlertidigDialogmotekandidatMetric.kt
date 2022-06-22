package no.nav.syfo.dialogmotekandidat.midlertidig

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE = "${METRICS_NS}_dialogmotekandidat_stoppunkt"
const val MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING = "${MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE}_created_kandidat_endring"
const val MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING = "${MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE}_skipped_not_kandidat_endring"

val COUNT_MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING: Counter = Counter
    .builder(MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING)
    .description("Counts the number of midlertidig-dialogmotekandidat-endring created from dialogmotekandidat-stoppunkt")
    .register(METRICS_REGISTRY)
val COUNT_MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING: Counter = Counter
    .builder(MIDLERTIDIG_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING)
    .description("Counts the number of skipped midlertidig-dialogmotekandidat-stoppunkt - not dialogmotekandidat-endring")
    .register(METRICS_REGISTRY)
