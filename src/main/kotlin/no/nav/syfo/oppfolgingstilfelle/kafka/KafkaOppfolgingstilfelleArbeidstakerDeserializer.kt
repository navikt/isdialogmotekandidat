package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KafkaOppfolgingstilfelleArbeidstakerDeserializer : Deserializer<KafkaOppfolgingstilfelleArbeidstaker> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(topic: String, data: ByteArray): KafkaOppfolgingstilfelleArbeidstaker =
        mapper.readValue(data, KafkaOppfolgingstilfelleArbeidstaker::class.java)

    override fun close() {}
}
