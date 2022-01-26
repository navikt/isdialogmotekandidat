package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.database.DatabaseInterface
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class KafkaOppfolgingstilfelleArbeidstakerService(
    val database: DatabaseInterface,
) {
    fun pollAndProcessRecords(
        kafkaConsumerOppfolgingstilfelleArbeidstaker: KafkaConsumer<String, KafkaOppfolgingstilfelleArbeidstaker>,
    ) {
        val records = kafkaConsumerOppfolgingstilfelleArbeidstaker.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            kafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync()
        }
    }
}
