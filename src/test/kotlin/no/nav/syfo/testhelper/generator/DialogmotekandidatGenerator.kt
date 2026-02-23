package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.Personident
import java.time.LocalDate

fun generateDialogmotekandidatStoppunktPlanlagt(
    arbeidstakerPersonIdent: Personident,
    planlagt: LocalDate,
): DialogmotekandidatStoppunkt = DialogmotekandidatStoppunkt.planlagt(
    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
    tilfelleStart = planlagt.minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
    tilfelleEnd = planlagt.plusDays(1),
)

fun generateDialogmotekandidatEndringStoppunkt(
    personIdentNumber: Personident,
): DialogmotekandidatEndring.Kandidat =
    DialogmotekandidatEndring.createKandidat(personident = personIdentNumber)

fun generateDialogmotekandidatEndringFerdigstilt(
    personIdentNumber: Personident,
): DialogmotekandidatEndring.Endring = DialogmotekandidatEndring.ferdigstiltDialogmote(
    personident = personIdentNumber
)
