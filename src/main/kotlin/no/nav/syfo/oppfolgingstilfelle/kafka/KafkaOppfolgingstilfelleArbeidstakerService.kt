package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.isDialogmotekandidat
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaOppfolgingstilfelleArbeidstakerService(
    val database: DatabaseInterface,
) {
    fun pollAndProcessRecords(
        kafkaConsumerOppfolgingstilfelleArbeidstaker: KafkaConsumer<String, KafkaOppfolgingstilfelleArbeidstaker>,
    ) {
        val records = kafkaConsumerOppfolgingstilfelleArbeidstaker.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync()
        }
    }

    private fun processRecords(
        consumerRecords: ConsumerRecords<String, KafkaOppfolgingstilfelleArbeidstaker>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach { consumerRecord ->
                if (consumerRecord.value() == null) {
                    log.error("Value of ConsumerRecord is null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected. key=${consumerRecord.key()} from topic: ${consumerRecord.topic()}, partiion=${consumerRecord.partition()}, offset=${consumerRecord.offset()}")
                    COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOMBSTONE.increment()
                    return
                }

                COUNT_KAFKA_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_READ.increment()
                log.info("Received ${KafkaOppfolgingstilfelleArbeidstaker::class.java.simpleName}, ready to process. id=${consumerRecord.key()}, timestamp=${consumerRecord.timestamp()}")

                receiveKafkaOppfolgingstilfelleArbeidstaker(
                    connection = connection,
                    kafkaOppfolgingstilfelleArbeidstaker = consumerRecord.value(),
                )
            }
            connection.commit()
        }
    }

    private fun receiveKafkaOppfolgingstilfelleArbeidstaker(
        connection: Connection,
        kafkaOppfolgingstilfelleArbeidstaker: KafkaOppfolgingstilfelleArbeidstaker,
    ) {
        val oppfolgingstilfelleArbeidstaker =
            kafkaOppfolgingstilfelleArbeidstaker.toOppfolgingstilfelleArbeidstaker()

        if (oppfolgingstilfelleArbeidstaker.isDialogmotekandidat()) {
            try {
                connection.createOppfolgingstilfelleArbeidstaker(
                    commit = false,
                    oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstaker,
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_CREATED.increment()
            } catch (noElementInsertedException: NoElementInsertedException) {
                log.warn(
                    "No ${KafkaOppfolgingstilfelleArbeidstaker::class.java.simpleName} was inserted into database, attempted to insert a duplicate"
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_DUPLICATE.increment()
            }
        } else {
            log.info(
                "No ${KafkaOppfolgingstilfelleArbeidstaker::class.java.simpleName} was inserted into database, not DialogmoteKandidat"
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfelleArbeidstakerService::class.java)
    }
}
