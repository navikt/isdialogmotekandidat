package no.nav.syfo.cronjob.dialogmotekandidat

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE = "${METRICS_NS}_cronjob_dialogmotekandidat_stoppunkt"
const val CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_UPDATE = "${CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE}_update_count"
const val CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_FAIL = "${CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_BASE}_fail_count"

val COUNT_CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_UPDATE: Counter = Counter
    .builder(CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_UPDATE)
    .description("Counts the number of updates in Cronjob - DialogmotekandidatStoppunktCronjob")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_FAIL: Counter = Counter
    .builder(CRONJOB_DIALOGMOTEKANDIDAT_STOPPUNKT_FAIL)
    .description("Counts the number of failures in Cronjob - DialogmotekandidatStoppunktCronjob")
    .register(METRICS_REGISTRY)
