package no.nav.syfo.infrastructure.clients.azuread

import java.io.Serializable
import java.time.LocalDateTime

data class AzureAdToken(
    val accessToken: String,
    val expires: LocalDateTime,
) : Serializable

fun AzureAdToken.isExpired() = this.expires < LocalDateTime.now().plusSeconds(60)
