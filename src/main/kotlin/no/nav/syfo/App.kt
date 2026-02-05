package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.wellknown.getWellKnown
import no.nav.syfo.infrastructure.cronjob.launchCronjobModule
import no.nav.syfo.infrastructure.database.DialogmotekandidatVurderingRepository
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.kafkaDialogmotekandidatEndringProducerConfig
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DialogmoteStatusEndringConsumer
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.launchKafkaTaskDialogmoteStatusEndring
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseConsumerService
import no.nav.syfo.infrastructure.kafka.identhendelse.launchKafkaTaskIdenthendelse
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.launchKafkaTaskOppfolgingstilfellePerson
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
        producer = KafkaProducer(
            kafkaDialogmotekandidatEndringProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )
    lateinit var dialogmotekandidatService: DialogmotekandidatService
    lateinit var dialogmotekandidatVurderingService: DialogmotekandidatVurderingService
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
            val dialogmotekandidatRepository = DialogmotekandidatRepository(applicationDatabase)
            dialogmotekandidatService = DialogmotekandidatService(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
                database = applicationDatabase,
                dialogmotekandidatRepository = dialogmotekandidatRepository,
            )
            dialogmotekandidatVurderingService = DialogmotekandidatVurderingService(
                database = applicationDatabase,
                dialogmotekandidatService = dialogmotekandidatService,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                dialogmotekandidatRepository = dialogmotekandidatRepository,
                dialogmotekandidatVurderingRepository = DialogmotekandidatVurderingRepository(applicationDatabase),
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                dialogmotekandidatService = dialogmotekandidatService,
                dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")

                val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
                    database = applicationDatabase,
                )
                val dialogmoteStatusEndringConsumer = DialogmoteStatusEndringConsumer(
                    database = applicationDatabase,
                    dialogmotekandidatRepository = dialogmotekandidatRepository,
                    dialogmotekandidatService = dialogmotekandidatService,
                    dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
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
                    dialogmoteStatusEndringConsumer = dialogmoteStatusEndringConsumer,
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
