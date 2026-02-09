package no.nav.syfo.infrastructure.kafka.dialogmotestatusendring

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.latest
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.*

class DialogmoteStatusEndringConsumer(
    private val database: DatabaseInterface,
    private val dialogmotekandidatRepository: DialogmotekandidatRepository,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    fun pollAndProcessRecords(consumer: KafkaConsumer<String, KDialogmoteStatusEndring>) {
        val records = consumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
        if (records.count() > 0) {
            processRecords(records = records)
            consumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KDialogmoteStatusEndring>) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            log.error("Value of ${tombstoneRecords.size} ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_TOMBSTONE.increment()
        }

        runBlocking {
            database.connection.use { connection ->
                validRecords.forEach { record ->
                    COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_READ.increment()
                    log.info("Received ${KDialogmoteStatusEndring::class.java.simpleName} with key=${record.key()}, ready to process.")
                    receiveKafkaDialogmoteStatusEndring(
                        connection = connection,
                        kafkaDialogmoteStatusEndring = record.value(),
                    )
                }
                connection.commit()
            }
        }
    }

    private suspend fun receiveKafkaDialogmoteStatusEndring(
        connection: Connection,
        kafkaDialogmoteStatusEndring: KDialogmoteStatusEndring,
    ) {
        val dialogmoteStatusEndring = DialogmoteStatusEndring.create(kafkaDialogmoteStatusEndring)
        if (!dialogmoteStatusEndring.isRelevant()) {
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_RELEVANT.increment()
            log.info("Skipped processing of ${KDialogmoteStatusEndring::class.java.simpleName} record, not relevant status-endring")
            return
        }

        connection.createDialogmoteStatus(
            dialogmoteStatusEndring = dialogmoteStatusEndring,
        )

        val latestDialogmotekandidatEndring =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                personident = dialogmoteStatusEndring.personIdentNumber,
                connection = connection
            )
                .latest()

        val isStatusendringAfterKandidat =
            latestDialogmotekandidatEndring?.kandidat == true && dialogmoteStatusEndring.createdAt.isAfter(latestDialogmotekandidatEndring.createdAt)
        if (isStatusendringAfterKandidat) {
            if (dialogmoteStatusEndring.type == DialogmoteStatusEndring.Type.INNKALT) {
                val avventList = dialogmotekandidatVurderingService.getAvvent(dialogmoteStatusEndring.personIdentNumber)
                avventList.filter { !it.isLukket }
                    .forEach { avvent ->
                        dialogmotekandidatVurderingService.lukkAvvent(connection, avvent)
                    }
            } else {
                val latestOppfolgingstilfelle = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
                    arbeidstakerPersonIdent = dialogmoteStatusEndring.personIdentNumber,
                )
                val newDialogmotekandidatEndring = dialogmoteStatusEndring.toDialogmotekandidatEndring()
                dialogmotekandidatService.createDialogmotekandidatEndring(
                    connection = connection,
                    dialogmotekandidatEndring = newDialogmotekandidatEndring,
                    tilfelleStart = latestOppfolgingstilfelle?.tilfelleStart,
                    unntak = null,
                )
                COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_CREATED_KANDIDATENDRING.increment()
            }
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_KANDIDATENDRING.increment()
            log.info("Processed ${KDialogmoteStatusEndring::class.java.simpleName} record, no DialogmotekandidatEndring created")
        }
    }

    private fun DialogmoteStatusEndring.toDialogmotekandidatEndring() =
        when (this.type) {
            DialogmoteStatusEndring.Type.FERDIGSTILT -> DialogmotekandidatEndring.ferdigstiltDialogmote(
                personIdentNumber = this.personIdentNumber,
            )
            DialogmoteStatusEndring.Type.LUKKET -> DialogmotekandidatEndring.lukketDialogmote(
                personIdentNumber = this.personIdentNumber,
            )
            else -> throw IllegalArgumentException("Cannot create DialogmotekandidatEndring for ${this.type}")
        }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteStatusEndringConsumer::class.java)
        private const val POLL_DURATION_SECONDS = 1L

        fun config(
            kafkaEnvironment: KafkaEnvironment,
        ): Properties {
            return Properties().apply {
                putAll(commonKafkaAivenConsumerConfig(kafkaEnvironment))
                // override group_id_config to start consuming the topic from the beginning
                // (since AUTO_OFFSET_RESET_CONFIG = "earliest")
                this[ConsumerConfig.GROUP_ID_CONFIG] = "isdialogmotekandidat-v2"

                this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
                this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName

                this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaEnvironment.aivenSchemaRegistryUrl
                this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
                this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
                    "${kafkaEnvironment.aivenRegistryUser}:${kafkaEnvironment.aivenRegistryPassword}"
                this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
            }
        }
    }
}
