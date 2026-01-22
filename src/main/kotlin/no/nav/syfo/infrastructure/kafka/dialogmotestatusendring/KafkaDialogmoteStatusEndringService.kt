package no.nav.syfo.infrastructure.kafka.dialogmotestatusendring

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndringList
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.latest
import no.nav.syfo.infrastructure.database.createDialogmoteStatus
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.application.OppfolgingstilfelleService
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaDialogmoteStatusEndringService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val dialogmotekandidatVurderingService: DialogmotekandidatVurderingService,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
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
            connection.getDialogmotekandidatEndringListForPerson(personident = dialogmoteStatusEndring.personIdentNumber)
                .toDialogmotekandidatEndringList()
                .latest()

        val isStatusendringAfterKandidat =
            latestDialogmotekandidatEndring?.kandidat == true && dialogmoteStatusEndring.createdAt.isAfter(latestDialogmotekandidatEndring.createdAt)
        if (isStatusendringAfterKandidat) {
            if (dialogmoteStatusEndring.type == DialogmoteStatusEndringType.INNKALT) {
                val avventList = dialogmotekandidatVurderingService.getAvvent(dialogmoteStatusEndring.personIdentNumber)
                avventList.forEach { avvent ->
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
            DialogmoteStatusEndringType.FERDIGSTILT -> DialogmotekandidatEndring.ferdigstiltDialogmote(
                personIdentNumber = this.personIdentNumber,
            )
            DialogmoteStatusEndringType.LUKKET -> DialogmotekandidatEndring.lukketDialogmote(
                personIdentNumber = this.personIdentNumber,
            )
            else -> throw IllegalArgumentException("Cannot create DialogmotekandidatEndring for ${this.type}")
        }

    private fun DialogmoteStatusEndring.isRelevant() =
        this.type == DialogmoteStatusEndringType.FERDIGSTILT || this.type == DialogmoteStatusEndringType.LUKKET || this.type == DialogmoteStatusEndringType.INNKALT

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmoteStatusEndringService::class.java)
        private const val POLL_DURATION_SECONDS = 1L
    }
}
