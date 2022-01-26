package no.nav.syfo.application

import io.ktor.application.*

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB"

data class Environment(
    val isdialogmotekandidatDbHost: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
    val isdialogmotekandidatDbPort: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
    val isdialogmotekandidatDbName: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
    val isdialogmotekandidatDbUsername: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
    val isdialogmotekandidatDbPassword: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),

    val kafka: ApplicationEnvironmentKafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    ),
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
