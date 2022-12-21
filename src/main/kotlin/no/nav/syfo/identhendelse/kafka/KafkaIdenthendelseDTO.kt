package no.nav.syfo.identhendelse.kafka

import no.nav.syfo.domain.PersonIdentNumber

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
) {
    val folkeregisterIdenter = identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }

    fun getActivePersonident(): PersonIdentNumber? = folkeregisterIdenter
        .find { it.gjeldende }
        ?.idnummer
        ?.let { PersonIdentNumber(it) }

    fun getInactivePersonidenter(): List<PersonIdentNumber> = folkeregisterIdenter
        .filter { !it.gjeldende }
        .map { PersonIdentNumber(it.idnummer) }
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
