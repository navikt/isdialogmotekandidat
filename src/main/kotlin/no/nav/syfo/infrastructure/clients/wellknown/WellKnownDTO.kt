package no.nav.syfo.infrastructure.clients.wellknown

data class WellKnownDTO(
    val authorization_endpoint: String,
    val issuer: String,
    val jwks_uri: String,
    val token_endpoint: String,
)

fun WellKnownDTO.toWellKnown() = WellKnown(
    issuer = this.issuer,
    jwksUri = this.jwks_uri,
)
