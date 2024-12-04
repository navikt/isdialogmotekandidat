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
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.cronjob.launchCronjobModule
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.DialogmotekandidatRepository
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.kafkaDialogmotekandidatEndringProducerConfig
import no.nav.syfo.dialogmotestatusendring.kafka.KafkaDialogmoteStatusEndringService
import no.nav.syfo.dialogmotestatusendring.kafka.launchKafkaTaskDialogmoteStatusEndring
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
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
    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.istilgangskontroll
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
    )

    val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = KafkaProducer(
            kafkaDialogmotekandidatEndringProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )
    lateinit var dialogmotekandidatService: DialogmotekandidatService
    lateinit var oppfolgingstilfelleService: OppfolgingstilfelleService

    val applicationEngineEnvironment = applicationEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
    }

    val server = embeddedServer(
        Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
                azureAdClient = azureAdClient,
                clientEnvironment = environment.clients.oppfolgingstilfelle,
            )
            oppfolgingstilfelleService = OppfolgingstilfelleService(
                oppfolgingstilfelleClient = oppfolgingstilfelleClient,
            )
            dialogmotekandidatService = DialogmotekandidatService(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
                database = applicationDatabase,
                dialogmotekandidatRepository = DialogmotekandidatRepository(applicationDatabase)
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                dialogmotekandidatService = dialogmotekandidatService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")

                val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
                    database = applicationDatabase,
                )
                val kafkaDialogmoteStatusEndringService = KafkaDialogmoteStatusEndringService(
                    database = applicationDatabase,
                    dialogmotekandidatService = dialogmotekandidatService,
                    oppfolgingstilfelleService = oppfolgingstilfelleService,
                )

                launchKafkaTaskOppfolgingstilfellePerson(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
                )
                launchKafkaTaskDialogmoteStatusEndring(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    kafkaDialogmoteStatusEndringService = kafkaDialogmoteStatusEndringService,
                )

                val identhendelseService = IdenthendelseService(
                    database = applicationDatabase,
                    pdlClient = pdlClient,
                )
                val identhendelseConsumerService = IdenthendelseConsumerService(
                    identhendelseService = identhendelseService,
                )
                launchKafkaTaskIdenthendelse(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    kafkaIdenthendelseConsumerService = identhendelseConsumerService,
                )

                launchCronjobModule(
                    applicationState = applicationState,
                    environment = environment,
                    dialogmotekandidatService = dialogmotekandidatService,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
