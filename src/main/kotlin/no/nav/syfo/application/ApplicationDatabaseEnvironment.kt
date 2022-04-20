package no.nav.syfo.application

data class ApplicationEnvironmentDatabase(
    val host: String,
    val port: String,
    val name: String,
    val username: String,
    val password: String,
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$host:$port/$name"
    }
}
