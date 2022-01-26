package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
    )

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.embeddedEnvironment.start()
}
