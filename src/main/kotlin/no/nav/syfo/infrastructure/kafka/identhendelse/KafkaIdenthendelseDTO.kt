package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.domain.Personident

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
) {
    val folkeregisterIdenter: List<Identifikator> = identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }

    fun getActivePersonident(): Personident? = folkeregisterIdenter
        .find { it.gjeldende }
        ?.let { Personident(it.idnummer) }

    fun getInactivePersonidenter(): List<Personident> = folkeregisterIdenter
        .filter { !it.gjeldende }
        .map { Personident(it.idnummer) }
}

data class Identifikator(
    val idnummer: String,
    val type: IdentType,
    val gjeldende: Boolean,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
