package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.cronjob.launchCronjobModule
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.kafkaDialogmotekandidatEndringProducerConfig
import no.nav.syfo.dialogmotestatusendring.kafka.KafkaDialogmoteStatusEndringService
import no.nav.syfo.dialogmotestatusendring.kafka.launchKafkaTaskDialogmoteStatusEndring
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = KafkaProducer(
            kafkaDialogmotekandidatEndringProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            databaseModule(
                databaseEnvironment = environment.database,
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

        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = applicationDatabase
        )
        val dialogmotekandidatService = DialogmotekandidatService(
            database = applicationDatabase,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
            dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        )
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = applicationDatabase,
        )
        val kafkaDialogmoteStatusEndringService = KafkaDialogmoteStatusEndringService(
            database = applicationDatabase,
            dialogmotekandidatService = dialogmotekandidatService,
        )

        if (environment.kafkaOppfolgingstilfellePersonProcessingEnabled) {
            launchKafkaTaskOppfolgingstilfellePerson(
                applicationState = applicationState,
                applicationKafkaEnvironment = environment.kafka,
                kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
            )
        }
        if (environment.kafkaDialogmoteStatusEndringProcessingEnabled) {
            launchKafkaTaskDialogmoteStatusEndring(
                applicationState = applicationState,
                applicationKafkaEnvironment = environment.kafka,
                kafkaDialogmoteStatusEndringService = kafkaDialogmoteStatusEndringService,
            )
        }
        if (environment.dialogmotekandidatStoppunktCronjobEnabled) {
            launchCronjobModule(
                applicationState = applicationState,
                environment = environment,
                dialogmotekandidatService = dialogmotekandidatService,
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
