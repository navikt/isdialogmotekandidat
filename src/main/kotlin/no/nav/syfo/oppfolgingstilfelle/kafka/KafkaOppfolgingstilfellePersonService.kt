package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.dialogmotekandidat.database.createDialogmotekandidatStoppunkt
import no.nav.syfo.oppfolgingstilfelle.*
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
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
        if (kafkaOppfolgingstilfellePerson.oppfolgingstilfelleList.isEmpty()) {
            log.warn("Skipped processing of record: No Oppfolgingstilfelle found in record.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE.increment()
            return
        }

        val latestOppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker? =
            kafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelleArbeidstaker()

        if (latestOppfolgingstilfelleArbeidstaker?.isDialogmotekandidat() == true) {
            createOppfolgingstilfelleArbeidstaker(
                connection = connection,
                oppfolgingstilfelleArbeidstaker = latestOppfolgingstilfelleArbeidstaker,
            ) {
                val dialogmotekandidatStoppunkt =
                    latestOppfolgingstilfelleArbeidstaker.toDialogmotekandidatStoppunktPlanlagt()
                connection.createDialogmotekandidatStoppunkt(
                    commit = false,
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunkt,
                )
            }
        } else {
            val oppfolgingstilfelleArbeidstakerList =
                kafkaOppfolgingstilfellePerson.toOppfolgingstilfelleArbeidstakerList()

            if (oppfolgingstilfelleArbeidstakerList.isEmpty()) {
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_ARBEIDSTAKER.increment()
                return
            }

            val previousOppfolgingstilfelleArbeidstakerKandidat: OppfolgingstilfelleArbeidstaker? =
                oppfolgingstilfelleArbeidstakerList.filter { oppfolgingstilfelleArbeidstaker ->
                    oppfolgingstilfelleArbeidstaker.isDialogmotekandidat()
                }.filter { oppfolgingstilfelleArbeidstaker ->
                    oppfolgingstilfelleArbeidstaker.tilfelleStart != latestOppfolgingstilfelleArbeidstaker?.tilfelleStart && oppfolgingstilfelleArbeidstaker.tilfelleEnd != latestOppfolgingstilfelleArbeidstaker?.tilfelleEnd
                }.maxByOrNull { oppfolgingstilfelleArbeidstaker ->
                    oppfolgingstilfelleArbeidstaker.tilfelleStart
                }

            if (previousOppfolgingstilfelleArbeidstakerKandidat == null) {
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_KANDIDAT.increment()
                return
            }

            createOppfolgingstilfelleArbeidstaker(
                connection = connection,
                oppfolgingstilfelleArbeidstaker = previousOppfolgingstilfelleArbeidstakerKandidat,
            ) {}
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_KANDIDAT_PREVIOUS_TILFELLE.increment()
        }
    }

    private fun createOppfolgingstilfelleArbeidstaker(
        connection: Connection,
        oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker,
        requestBlock: () -> Unit,
    ) {
        try {
            connection.createOppfolgingstilfelleArbeidstaker(
                commit = false,
                oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstaker,
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_ARBEIDSTAKER_CREATED.increment()
            requestBlock()
        } catch (noElementInsertedException: NoElementInsertedException) {
            log.warn(
                "No ${KafkaOppfolgingstilfellePerson::class.java.simpleName} was inserted into database, attempted to insert a duplicate"
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_ARBEIDSTAKER_DUPLICATE.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
