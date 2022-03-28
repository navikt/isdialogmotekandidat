package no.nav.syfo.dialogmotestatusendring.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

class KafkaDialogmoteStatusEndringService(
    val database: DatabaseInterface,
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
        consumerRecords.forEach { consumerRecord ->
            log.info("Received KDialogmoteStatusEndring record with key: ${consumerRecord.key()}")
            log.info("KDialogmoteStatusEndring: ${consumerRecord.value()}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmoteStatusEndringService::class.java)
        private const val POLL_DURATION_SECONDS = 1L
    }
}
