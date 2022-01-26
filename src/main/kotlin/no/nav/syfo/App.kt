package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfelleArbeidstakerService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfelleArbeidstaker
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            databaseModule(
                environment = environment
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready")

        val kafkaOppfolgingstilfelleArbeidstakerService = KafkaOppfolgingstilfelleArbeidstakerService(
            database = applicationDatabase,
        )

        if (environment.kafkaOppfolgingstilfelleArbeidstakerProcessingEnabled) {
            launchKafkaTaskOppfolgingstilfelleArbeidstaker(
                applicationState = applicationState,
                applicationEnvironmentKafka = environment.kafka,
                kafkaOppfolgingstilfelleArbeidstakerService = kafkaOppfolgingstilfelleArbeidstakerService,
            )
        }
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = false)
}
