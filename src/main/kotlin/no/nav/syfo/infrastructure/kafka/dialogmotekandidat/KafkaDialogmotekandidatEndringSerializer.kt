package no.nav.syfo.infrastructure.kafka.dialogmotekandidat

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaDialogmotekandidatEndringSerializer : Serializer<DialogmotekandidatEndringRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: DialogmotekandidatEndringRecord?): ByteArray = mapper.writeValueAsBytes(data)
}
