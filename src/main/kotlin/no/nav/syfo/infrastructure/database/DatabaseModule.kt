package no.nav.syfo.infrastructure.database

import io.ktor.server.application.*
import no.nav.syfo.isDev
import no.nav.syfo.isProd

lateinit var applicationDatabase: DatabaseInterface
fun Application.databaseModule(
    databaseEnvironment: DatabaseEnvironment,
) {
    isDev {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/isdialogmotekandidat_dev",
                password = "password",
                username = "username",
            )
        )
    }

    isProd {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = databaseEnvironment.jdbcUrl(),
                username = databaseEnvironment.username,
                password = databaseEnvironment.password,
            )
        )
    }
}
