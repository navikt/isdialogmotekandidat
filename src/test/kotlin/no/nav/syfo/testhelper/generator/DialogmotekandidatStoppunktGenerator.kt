package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunkt
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.defaultZoneOffset
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotekandidatStoppunktPlanlagt(
    arbeidstakerPersonIdent: PersonIdentNumber,
    planlagt: LocalDate,
): DialogmotekandidatStoppunkt = DialogmotekandidatStoppunkt(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(defaultZoneOffset),
    personIdent = arbeidstakerPersonIdent,
    processedAt = null,
    status = DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT,
    stoppunktPlanlagt = planlagt,
)
