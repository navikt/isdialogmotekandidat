package no.nav.syfo.application

import io.ktor.server.application.*

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISDIALOGMOTEKANDIDAT_ISDIALOGMOTEKANDIDAT_DB"

data class Environment(
    val database: ApplicationEnvironmentDatabase = ApplicationEnvironmentDatabase(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
    ),

    val electorPath: String = getEnvVar("ELECTOR_PATH"),

    val kafka: ApplicationEnvironmentKafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    ),
    val kafkaOppfolgingstilfellePersonProcessingEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_OPPFOLGINGSTILFELLE_PERSON_PROCESSING_ENABLED").toBoolean(),
    val dialogmotekandidatStoppunktCronjobEnabled: Boolean = getEnvVar("TOGGLE_DIALOGMOTEKANDIDAT_STOPPUNKT_CRONJOB_ENABLED").toBoolean(),
    val kafkaDialogmoteStatusEndringProcessingEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_DIALOGMOTE_STATUS_ENDRING_PROCESSING_ENABLED").toBoolean()
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
