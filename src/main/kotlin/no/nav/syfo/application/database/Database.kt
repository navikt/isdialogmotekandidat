package no.nav.syfo.application.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet

data class DatabaseConfig(
    val jdbcUrl: String,
    val password: String,
    val username: String,
    val poolSize: Int = 4
)

class Database(
    private val daoConfig: DatabaseConfig
) : DatabaseInterface {

    override val connection: Connection
        get() = dataSource.connection

    private var dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = daoConfig.jdbcUrl
            username = daoConfig.username
            password = daoConfig.password
            maximumPoolSize = daoConfig.poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            metricsTrackerFactory = PrometheusMetricsTrackerFactory()
            validate()
        }
    )

    init {
        runFlywayMigrations()
    }

    private fun runFlywayMigrations() = Flyway.configure().run {
        dataSource(
            daoConfig.jdbcUrl,
            daoConfig.username,
            daoConfig.password
        )
        load().migrate().migrationsExecuted
    }
}

interface DatabaseInterface {
    val connection: Connection
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
