package no.nav.syfo.infrastructure.clients.veiledertilgang

data class Tilgang(
    val erGodkjent: Boolean,
    val fullTilgang: Boolean = false,
)
