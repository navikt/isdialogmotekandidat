package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.RoutingContext
import no.nav.syfo.api.exception.UnauthorizedException

const val JWT_CLAIM_AZP = "azp"
const val JWT_CLAIM_NAVIDENT = "NAVident"

fun RoutingContext.getBearerHeader(): String? {
    return this.call.getBearerHeader()
}

fun RoutingContext.getCallId(): String {
    return this.call.getCallId()
}

fun ApplicationCall.getCallId(): String {
    return this.request.headers[NAV_CALL_ID_HEADER].toString()
}

fun ApplicationCall.getBearerHeader(): String? {
    return this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
}

fun ApplicationCall.getConsumerClientId(): String? =
    getBearerHeader()?.let { token ->
        runCatching {
            JWT.decode(token).claims[JWT_CLAIM_AZP]?.asString()
        }.getOrNull()
    }

fun ApplicationCall.getNAVIdent(): String {
    val token = getBearerHeader() ?: throw UnauthorizedException("No Authorization header supplied")
    return runCatching {
        JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString()
    }.getOrElse {
        throw UnauthorizedException("Invalid Authorization token")
    } ?: throw UnauthorizedException("Missing NAVident in private claims")
}

fun RoutingContext.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER]
}
