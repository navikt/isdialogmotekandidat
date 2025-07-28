package no.nav.syfo.infrastructure.kafka.dialogmotekandidat

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaDialogmotekandidatEndringSerializer : Serializer<KafkaDialogmotekandidatEndring> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaDialogmotekandidatEndring?): ByteArray = mapper.writeValueAsBytes(data)
}
