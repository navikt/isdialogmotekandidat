package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

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
    getBearerHeader()?.let {
        JWT.decode(it).claims[JWT_CLAIM_AZP]?.asString()
    }

fun ApplicationCall.getNAVIdent(): String {
    val token = getBearerHeader() ?: throw Error("No Authorization header supplied")
    return JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw Error("Missing NAVident in private claims")
}

fun RoutingContext.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER]
}
