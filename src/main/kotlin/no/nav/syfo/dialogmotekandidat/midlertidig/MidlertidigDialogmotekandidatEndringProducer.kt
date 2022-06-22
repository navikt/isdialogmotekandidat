package no.nav.syfo.dialogmotekandidat.midlertidig

import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.toKafkaDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class MidlertidigDialogmotekandidatEndringProducer(
    private val kafkaProducerDialogmotekandidatEndring: KafkaProducer<String, KafkaDialogmotekandidatEndring>,
) {
    fun sendMidlertidigDialogmotekandidatEndring(
        dialogmotekandidatEndring: DialogmotekandidatEndring,
    ) {
        val kafkaDialogmotekandidatEndring = dialogmotekandidatEndring.toKafkaDialogmotekandidatEndring()
        val key = UUID.nameUUIDFromBytes(kafkaDialogmotekandidatEndring.personIdentNumber.toByteArray()).toString()
        try {
            kafkaProducerDialogmotekandidatEndring.send(
                ProducerRecord(
                    MIDLERTIDIG_DIALOGMOTEKANDIDAT_TOPIC,
                    key,
                    kafkaDialogmotekandidatEndring
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaDialogmotekandidatEndring with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val MIDLERTIDIG_DIALOGMOTEKANDIDAT_TOPIC = "teamsykefravr.isdialogmotekandidat-dialogmotekandidat-midlertidig"
        private val log = LoggerFactory.getLogger(MidlertidigDialogmotekandidatEndringProducer::class.java)
    }
}
