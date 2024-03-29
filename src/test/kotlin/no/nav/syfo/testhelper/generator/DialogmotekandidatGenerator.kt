package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate

fun generateDialogmotekandidatStoppunktPlanlagt(
    arbeidstakerPersonIdent: PersonIdentNumber,
    planlagt: LocalDate,
): DialogmotekandidatStoppunkt = DialogmotekandidatStoppunkt.planlagt(
    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
    tilfelleStart = planlagt.minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
    tilfelleEnd = planlagt.plusDays(1),
)

fun generateDialogmotekandidatEndringStoppunkt(
    personIdentNumber: PersonIdentNumber,
): DialogmotekandidatEndring = DialogmotekandidatEndring.stoppunktKandidat(
    personIdentNumber = personIdentNumber
)

fun generateDialogmotekandidatEndringFerdigstilt(
    personIdentNumber: PersonIdentNumber,
): DialogmotekandidatEndring = DialogmotekandidatEndring.ferdigstiltDialogmote(
    personIdentNumber = personIdentNumber
)
