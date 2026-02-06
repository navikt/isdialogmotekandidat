package no.nav.syfo.infrastructure.kafka.identhendelse

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import kotlinx.coroutines.delay
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.commonKafkaAivenConsumerConfig
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

class IdenthendelseConsumer(
    private val identhendelseService: IdenthendelseService,
) {
    suspend fun pollAndProcessRecords(consumer: KafkaConsumer<String, GenericRecord>) {
        try {
            val records = consumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
            if (records.count() > 0) {
                records.forEach { record ->
                    if (record.value() != null) {
                        identhendelseService.handleIdenthendelse(record.value().toKafkaIdenthendelseDTO())
                    } else {
                        log.warn("Identhendelse: Value of ConsumerRecord from topic $PDL_AKTOR_TOPIC is null, probably due to a tombstone. Contact the owner of the topic if an error is suspected")
                        COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE.increment()
                    }
                }
                consumer.commitSync()
            }
        } catch (ex: Exception) {
            log.warn("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
            consumer.unsubscribe()
            delay(DELAY_ON_ERROR_SECONDS.seconds)
        }
    }

    companion object {
        private const val POLL_DURATION_SECONDS = 10L
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.identhendelse")

        fun config(
            kafkaEnvironment: KafkaEnvironment,
        ): Properties =
            Properties().apply {
                putAll(commonKafkaAivenConsumerConfig(kafkaEnvironment))
                this[ConsumerConfig.GROUP_ID_CONFIG] = "isdialogmotekandidat-v1"
                this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName
                this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaEnvironment.aivenSchemaRegistryUrl
                this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = false
                this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
                    "${kafkaEnvironment.aivenRegistryUser}:${kafkaEnvironment.aivenRegistryPassword}"
                this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
            }
    }
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    val identifikatorer = (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Identifikator(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
    return KafkaIdenthendelseDTO(identifikatorer)
}
