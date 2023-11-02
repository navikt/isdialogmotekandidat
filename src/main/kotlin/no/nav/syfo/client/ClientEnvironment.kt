package no.nav.syfo.client

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment,
    val oppfolgingstilfelle: ClientEnvironment,
    val pdl: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
