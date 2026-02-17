package no.nav.syfo.infrastructure.kafka.dialogmotekandidat

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.commonKafkaAivenConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class DialogmotekandidatEndringProducer(
    private val producer: KafkaProducer<String, DialogmotekandidatEndringRecord>,
) {
    fun sendDialogmotekandidatEndring(
        dialogmotekandidatEndring: DialogmotekandidatEndring,
        tilfelleStart: LocalDate?,
    ) {
        val record = DialogmotekandidatEndringRecord.from(
            dialogmoteKandidatEndring = dialogmotekandidatEndring,
            tilfelleStart = tilfelleStart,
        )
        val key = UUID.nameUUIDFromBytes(record.personIdentNumber.toByteArray()).toString()
        try {
            producer.send(
                ProducerRecord(
                    DIALOGMOTEKANDIDAT_TOPIC,
                    key,
                    record
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

        fun config(kafkaEnvironment: KafkaEnvironment): Properties =
            Properties().apply {
                putAll(commonKafkaAivenConfig(kafkaEnvironment))
                this[ProducerConfig.ACKS_CONFIG] = "all"
                this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
                this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
                this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
                this[ProducerConfig.RETRIES_CONFIG] = "100000"
                this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
                this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaDialogmotekandidatEndringSerializer::class.java.canonicalName
            }
    }
}

data class DialogmotekandidatEndringRecord(
    val uuid: String,
    val createdAt: OffsetDateTime,
    val personIdentNumber: String,
    val kandidat: Boolean,
    val arsak: String,
    val unntakArsak: String?,
    val tilfelleStart: LocalDate?,
    val unntakVeilederident: String?,
) {
    companion object {
        fun from(dialogmoteKandidatEndring: DialogmotekandidatEndring, tilfelleStart: LocalDate?): DialogmotekandidatEndringRecord {
            val unntak = dialogmoteKandidatEndring as? DialogmotekandidatEndring.Unntak
            return DialogmotekandidatEndringRecord(
                uuid = dialogmoteKandidatEndring.uuid.toString(),
                createdAt = dialogmoteKandidatEndring.createdAt,
                personIdentNumber = dialogmoteKandidatEndring.personident.value,
                kandidat = dialogmoteKandidatEndring.kandidat,
                arsak = dialogmoteKandidatEndring.arsak.name,
                unntakArsak = unntak?.unntakArsak?.name,
                tilfelleStart = tilfelleStart,
                unntakVeilederident = unntak?.createdBy,
            )
        }
    }
}
