package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
) {
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = externalMockEnvironment.database
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = externalMockEnvironment.database,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatService = dialogmotekandidatService,
    )
}
