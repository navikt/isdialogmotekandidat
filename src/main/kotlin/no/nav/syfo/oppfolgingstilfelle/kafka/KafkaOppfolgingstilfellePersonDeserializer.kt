package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KafkaOppfolgingstilfellePersonDeserializer : Deserializer<KafkaOppfolgingstilfellePerson> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(topic: String, data: ByteArray): KafkaOppfolgingstilfellePerson =
        mapper.readValue(data, KafkaOppfolgingstilfellePerson::class.java)

    override fun close() {}
}
