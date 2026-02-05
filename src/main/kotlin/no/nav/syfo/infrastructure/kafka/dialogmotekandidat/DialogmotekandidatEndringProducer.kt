package no.nav.syfo.infrastructure.kafka.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Unntak
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

class DialogmotekandidatEndringProducer(
    private val producer: KafkaProducer<String, KafkaDialogmotekandidatEndring>,
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
            producer.send(
                ProducerRecord(
                    DIALOGMOTEKANDIDAT_TOPIC,
                    key,
                    kafkaDialogmotekandidatEndring
                )
            ).also { it.get() }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaDialogmotekandidatEndring with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        private const val DIALOGMOTEKANDIDAT_TOPIC = "teamsykefravr.isdialogmotekandidat-dialogmotekandidat"
        private val log = LoggerFactory.getLogger(DialogmotekandidatEndringProducer::class.java)
    }
}
