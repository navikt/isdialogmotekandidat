package no.nav.syfo.infrastructure.clients.wellknown

data class WellKnown(
    val issuer: String,
    val jwksUri: String,
)
