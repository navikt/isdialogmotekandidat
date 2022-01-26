package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.oppfolgingstilfelle.kafka.OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
