package no.nav.syfo.dialogmotekandidat.kafka

import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.toKafkaDialogmotekandidatEndring
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class DialogmotekandidatEndringProducer(
    private val kafkaProducerDialogmotekandidatEndring: KafkaProducer<String, KafkaDialogmotekandidatEndring>,
) {
    fun sendDialogmotekandidat(
        dialogmotekandidatEndring: DialogmotekandidatEndring,
    ) {
        val key = dialogmotekandidatEndring.uuid.toString()
        try {
            val kafkaDialogmotekandidatEndring = dialogmotekandidatEndring.toKafkaDialogmotekandidatEndring()
            kafkaProducerDialogmotekandidatEndring.send(
                ProducerRecord(
                    DIALOGMOTEKANDIDAT_TOPIC,
                    key,
                    kafkaDialogmotekandidatEndring
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaDialogmotekandidat with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val DIALOGMOTEKANDIDAT_TOPIC = "teamsykefravr.isdialogmotekandidat-dialogmotekandidat"
        private val log = LoggerFactory.getLogger(DialogmotekandidatEndringProducer::class.java)
    }
}
