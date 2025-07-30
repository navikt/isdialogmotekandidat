package no.nav.syfo.util

import io.ktor.server.routing.*
import no.nav.syfo.api.exception.ForbiddenAccessVeilederException
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.Personident

suspend fun RoutingContext.validateVeilederAccess(
    action: String,
    personIdentToAccess: Personident,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

    val hasVeilederAccess = veilederTilgangskontrollClient.hasAccess(
        callId = callId,
        personIdent = personIdentToAccess,
        token = token,
    )
    if (hasVeilederAccess) {
        requestBlock()
    } else {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}
