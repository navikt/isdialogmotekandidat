package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.dialogmotekandidat.database.createDialogmotekandidatStoppunkt
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat
import no.nav.syfo.oppfolgingstilfelle.toDialogmotekandidatStoppunktPlanlagt
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
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
        database.connection.use { connection ->
            consumerRecords.forEach { consumerRecord ->
                if (consumerRecord.value() == null) {
                    log.error("Value of ConsumerRecord is null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected. key=${consumerRecord.key()} from topic: ${consumerRecord.topic()}, partiion=${consumerRecord.partition()}, offset=${consumerRecord.offset()}")
                    COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE.increment()
                    return
                }

                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ.increment()
                log.info("Received ${KafkaOppfolgingstilfellePerson::class.java.simpleName}, ready to process. id=${consumerRecord.key()}, timestamp=${consumerRecord.timestamp()}")

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
        val latestTilfelle = kafkaOppfolgingstilfellePerson.oppfolgingstilfelleList.maxByOrNull {
            it.start
        }
        if (latestTilfelle == null) {
            log.warn("SKipped processing of record: No latest Oppfolgingstilfelle found in record.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE.increment()
            return
        }
        if (!latestTilfelle.arbeidstakerAtTilfelleEnd) {
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_ARBEIDSTAKER.increment()
            return
        }

        val oppfolgingstilfelleArbeidstaker = kafkaOppfolgingstilfellePerson.toOppfolgingstilfelleArbeidstaker(
            latestTilfelle = latestTilfelle,
        )

        if (oppfolgingstilfelleArbeidstaker.isDialogmotekandidat()) {
            try {
                connection.createOppfolgingstilfelleArbeidstaker(
                    commit = false,
                    oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstaker,
                )
                val dialogmotekandidatStoppunkt =
                    oppfolgingstilfelleArbeidstaker.toDialogmotekandidatStoppunktPlanlagt()
                connection.createDialogmotekandidatStoppunkt(
                    commit = false,
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunkt,
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_ARBEIDSTAKER_CREATED.increment()
            } catch (noElementInsertedException: NoElementInsertedException) {
                log.warn(
                    "No ${KafkaOppfolgingstilfellePerson::class.java.simpleName} was inserted into database, attempted to insert a duplicate"
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_ARBEIDSTAKER_DUPLICATE.increment()
            }
        } else {
            log.info(
                "No ${KafkaOppfolgingstilfellePerson::class.java.simpleName} was inserted into database, not DialogmoteKandidat"
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
