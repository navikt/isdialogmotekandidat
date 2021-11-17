package no.nav.syfo.application

import io.ktor.application.*

data class Environment(
    val isdialogmotekandidatDbHost: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB_HOST"),
    val isdialogmotekandidatDbPort: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB_PORT"),
    val isdialogmotekandidatDbName: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB_DATABASE"),
    val isdialogmotekandidatDbUsername: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB_USERNAME"),
    val isdialogmotekandidatDbPassword: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB_PASSWORD"),
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$isdialogmotekandidatDbHost:$isdialogmotekandidatDbPort/$isdialogmotekandidatDbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
