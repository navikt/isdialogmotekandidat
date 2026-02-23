package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.*

class OppfolgingstilfellePersonConsumer(
    val database: DatabaseInterface,
) {
    fun pollAndProcessRecords(
        consumer: KafkaConsumer<String, KafkaOppfolgingstilfellePerson>,
    ) {
        val records = consumer.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                records = records,
            )
            consumer.commitSync()
        }
    }

    private fun processRecords(
        records: ConsumerRecords<String, KafkaOppfolgingstilfellePerson>,
    ) {
        val (tombstoneRecordList, recordsValid) = records.partition {
            it.value() == null
        }

        processTombstoneRecordList(
            tombstoneRecordList = tombstoneRecordList,
        )
        processRelevantRecordList(
            records = recordsValid,
        )
    }

    private fun processTombstoneRecordList(
        tombstoneRecordList: List<ConsumerRecord<String, KafkaOppfolgingstilfellePerson>>,
    ) {
        if (tombstoneRecordList.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecordList.size
            log.error("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE.increment(numberOfTombstones.toDouble())
        }
    }

    private fun processRelevantRecordList(
        records: List<ConsumerRecord<String, KafkaOppfolgingstilfellePerson>>,
    ) {
        database.connection.use { connection ->
            records.forEach { consumerRecord ->
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ.increment()

                receiveKafkaOppfolgingstilfellePerson(
                    connection = connection,
                    kafkaOppfolgingstilfellePerson = consumerRecord.value(),
                )
            }
            connection.commit()
        }
    }

    private fun receiveKafkaOppfolgingstilfellePerson(
        connection: Connection,
        kafkaOppfolgingstilfellePerson: KafkaOppfolgingstilfellePerson,
    ) {
        if (kafkaOppfolgingstilfellePerson.oppfolgingstilfelleList.isEmpty()) {
            log.warn("Skipped processing of record: No Oppfolgingstilfelle found in record.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE.increment()
            return
        }

        val latestOppfolgingstilfelle = kafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelle()
        createStoppunktIfKandidattilfelle(latestOppfolgingstilfelle, connection)

        val currentOppfolgingstilfelle = kafkaOppfolgingstilfellePerson.toCurrentOppfolgingstilfelle()
        if (currentOppfolgingstilfelle != null && currentOppfolgingstilfelle != latestOppfolgingstilfelle) {
            createStoppunktIfKandidattilfelle(currentOppfolgingstilfelle, connection)
        }
    }

    private fun createStoppunktIfKandidattilfelle(
        oppfolgingstilfelle: Oppfolgingstilfelle?,
        connection: Connection,
    ) {
        if (oppfolgingstilfelle?.isDialogmotekandidat() == true) {
            val dialogmotekandidatStoppunkt =
                oppfolgingstilfelle.toDialogmotekandidatStoppunktPlanlagt()
            connection.createDialogmotekandidatStoppunkt(
                commit = false,
                dialogmotekandidatStoppunkt = dialogmotekandidatStoppunkt,
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_PLANLAGT_KANDIDAT.increment()
        } else {
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfellePersonConsumer::class.java)

        fun config(kafkaEnvironment: KafkaEnvironment): Properties =
            Properties().apply {
                putAll(commonKafkaAivenConsumerConfig(kafkaEnvironment))
                this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
                this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
                    KafkaOppfolgingstilfellePersonDeserializer::class.java.canonicalName
            }
    }
}
