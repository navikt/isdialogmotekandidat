package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatStoppunkt
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.isDialogmotekandidat
import no.nav.syfo.domain.toDialogmotekandidatStoppunktPlanlagt
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaOppfolgingstilfellePersonService(
    val database: DatabaseInterface,
) {
    fun pollAndProcessRecords(
        kafkaConsumerOppfolgingstilfellePerson: KafkaConsumer<String, KafkaOppfolgingstilfellePerson>,
    ) {
        val records = kafkaConsumerOppfolgingstilfellePerson.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumerOppfolgingstilfellePerson.commitSync()
        }
    }

    private fun processRecords(
        consumerRecords: ConsumerRecords<String, KafkaOppfolgingstilfellePerson>,
    ) {
        val (tombstoneRecordList, recordsValid) = consumerRecords.partition {
            it.value() == null
        }

        processTombstoneRecordList(
            tombstoneRecordList = tombstoneRecordList,
        )
        processRelevantRecordList(
            consumerRecords = recordsValid,
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
        consumerRecords: List<ConsumerRecord<String, KafkaOppfolgingstilfellePerson>>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach { consumerRecord ->
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
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
