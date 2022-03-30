package no.nav.syfo.dialogmotestatusendring.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getLatestDialogmotekandidatEndringForPerson
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusEndring
import no.nav.syfo.dialogmotestatusendring.domain.isFerdigstilt
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaDialogmoteStatusEndringService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
) {
    fun pollAndProcessRecords(
        kafkaConsumerDialogmoteStatusEndring: KafkaConsumer<String, KDialogmoteStatusEndring>,
    ) {
        val consumerRecords = kafkaConsumerDialogmoteStatusEndring.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
        if (consumerRecords.count() > 0) {
            processRecords(
                consumerRecords = consumerRecords,
            )
            kafkaConsumerDialogmoteStatusEndring.commitSync()
        }
    }

    private fun processRecords(consumerRecords: ConsumerRecords<String, KDialogmoteStatusEndring>) {
        val (tombstoneRecords, validRecords) = consumerRecords.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            log.error("Value of ${tombstoneRecords.size} ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_TOMBSTONE.increment()
        }

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

    private fun receiveKafkaDialogmoteStatusEndring(
        connection: Connection,
        kafkaDialogmoteStatusEndring: KDialogmoteStatusEndring,
    ) {
        val dialogmoteStatusEndring = DialogmoteStatusEndring.create(kafkaDialogmoteStatusEndring)
        if (!dialogmoteStatusEndring.isFerdigstilt()) {
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_FERDIGSTILT.increment()
            log.info("Skipped processing of ${KDialogmoteStatusEndring::class.java.simpleName} record, not Ferdigstilt status-endring")
            return
        }

        val latestDialogmotekandidatEndring =
            connection.getLatestDialogmotekandidatEndringForPerson(personIdent = dialogmoteStatusEndring.personIdentNumber)
                ?.toDialogmotekandidatEndring()

        if (shouldCreateDialogmotekandidatEndring(
                latestDialogmotekandidatEndring = latestDialogmotekandidatEndring,
                ferdigstiltStatusEndring = dialogmoteStatusEndring
            )
        ) {
            val newDialogmotekandidatEndring =
                DialogmotekandidatEndring.ferdigstiltDialogmote(personIdentNumber = dialogmoteStatusEndring.personIdentNumber)
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring
            )
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_CREATED_KANDIDATENDRING.increment()
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUS_ENDRING_SKIPPED_NOT_KANDIDATENDRING.increment()
            log.info("Processed ${KDialogmoteStatusEndring::class.java.simpleName} record, no DialogmotekandidatEndring created")
        }
    }

    private fun shouldCreateDialogmotekandidatEndring(
        latestDialogmotekandidatEndring: DialogmotekandidatEndring?,
        ferdigstiltStatusEndring: DialogmoteStatusEndring,
    ): Boolean {
        return latestDialogmotekandidatEndring?.kandidat == true && ferdigstiltStatusEndring.createdAt.isAfter(
            latestDialogmotekandidatEndring.createdAt
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmoteStatusEndringService::class.java)
        private const val POLL_DURATION_SECONDS = 1L
    }
}
