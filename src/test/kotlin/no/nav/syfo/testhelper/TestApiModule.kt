package no.nav.syfo.testhelper

import io.ktor.server.application.*
import io.mockk.mockk
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.midlertidig.MidlertidigDialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.midlertidig.MidlertidigDialogmotekandidatService
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    midlertidigDialogmotekandidatEndringProducer: MidlertidigDialogmotekandidatEndringProducer = mockk<MidlertidigDialogmotekandidatEndringProducer>()
) {
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = externalMockEnvironment.database
    )
    val midlertidigDialogmotekandidatService = MidlertidigDialogmotekandidatService(
        database = externalMockEnvironment.database,
        midlertidigDialogmotekandidatEndringProducer = midlertidigDialogmotekandidatEndringProducer,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = externalMockEnvironment.database,
        dialogmotekandidatStoppunktCronjobEnabled = externalMockEnvironment.environment.dialogmotekandidatStoppunktCronjobEnabled,
        midlertidigDialogmotekandidatStoppunktCronjobEnabled = externalMockEnvironment.environment.midlertidigDialogmotekandidatStoppunktCronjobEnabled,
        midlertidigDialogmotekandidatService = midlertidigDialogmotekandidatService,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        dialogmotekandidatService = dialogmotekandidatService,
    )
}
