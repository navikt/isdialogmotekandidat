package no.nav.syfo.dialogmotekandidat.kafka

import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.domain.toKafkaDialogmotekandidatEndring
import no.nav.syfo.unntak.domain.Unntak
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

class DialogmotekandidatEndringProducer(
    private val kafkaProducerDialogmotekandidatEndring: KafkaProducer<String, KafkaDialogmotekandidatEndring>,
) {
    fun sendDialogmotekandidatEndring(
        dialogmotekandidatEndring: DialogmotekandidatEndring,
        tilfelleStart: LocalDate?,
        unntak: Unntak?,
    ) {
        val kafkaDialogmotekandidatEndring = dialogmotekandidatEndring.toKafkaDialogmotekandidatEndring(
            tilfelleStart = tilfelleStart,
            unntak = unntak,
        )
        val key = UUID.nameUUIDFromBytes(kafkaDialogmotekandidatEndring.personIdentNumber.toByteArray()).toString()
        try {
            kafkaProducerDialogmotekandidatEndring.send(
                ProducerRecord(
                    DIALOGMOTEKANDIDAT_TOPIC,
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
        const val DIALOGMOTEKANDIDAT_TOPIC = "teamsykefravr.isdialogmotekandidat-dialogmotekandidat"
        private val log = LoggerFactory.getLogger(DialogmotekandidatEndringProducer::class.java)
    }
}
