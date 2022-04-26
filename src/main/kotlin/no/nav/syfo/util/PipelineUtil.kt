package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.pipeline.*

const val JWT_CLAIM_AZP = "azp"

fun PipelineContext<out Unit, ApplicationCall>.getBearerHeader(): String? {
    return this.call.getBearerHeader()
}

fun PipelineContext<out Unit, ApplicationCall>.getCallId(): String {
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

fun PipelineContext<out Unit, ApplicationCall>.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER]
}
