package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.generateKafkaOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.shouldBeEqualToOffsetDateTime
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class KafkaOppfolgingstilfelleArbeidstakerServiceSpek : Spek({

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val kafkaSyketilfellebitService = KafkaOppfolgingstilfelleArbeidstakerService(
            database = database,
        )

        val partition = 0
        val oppfolgingstilfelleArbeidstakerTopicPartition = TopicPartition(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
        )

        val kafkaOppfolgingstilfelleArbeidstaker = generateKafkaOppfolgingstilfelleArbeidstaker(
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        )
        val kafkaOppfolgingstilfelleArbeidstakerRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
            1,
            "key1",
            kafkaOppfolgingstilfelleArbeidstaker,
        )

        val mockKafkaConsumerOppfolgingstilfelleArbeidstaker =
            mockk<KafkaConsumer<String, KafkaOppfolgingstilfelleArbeidstaker>>()
        every { mockKafkaConsumerOppfolgingstilfelleArbeidstaker.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                    kafkaOppfolgingstilfelleArbeidstakerRecord,
                )
            )
        )
        every { mockKafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync() } returns Unit

        describe("${KafkaOppfolgingstilfelleArbeidstakerService::class.java.simpleName}: Poll and proceess reccords") {
            describe("Receive records, store and retrieve oppfolgingstilfelleArbeidstaker for PersonIdent") {
                it("should return OppfolgingstilfelleArbeidstaker created from KafkaOppfolgingstilfelleArbeidstaker") {
                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfelleArbeidstaker = mockKafkaConsumerOppfolgingstilfelleArbeidstaker,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker? =
                        database.getOppfolgingstilfelleArbeidstaker(
                            arbeidstakerPersonIdent = PersonIdentNumber(kafkaOppfolgingstilfelleArbeidstaker.personIdentNumber)
                        )?.toOppfolgingstilfelleArbeidstaker()

                    oppfolgingstilfelleArbeidstaker.shouldNotBeNull()

                    val latestTilfelle =
                        kafkaOppfolgingstilfelleArbeidstaker.oppfolgingstilfelleList.maxByOrNull { it.start }
                            ?: throw RuntimeException("No Oppfolgingstilfelle found")

                    oppfolgingstilfelleArbeidstaker.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfelleArbeidstaker.personIdentNumber
                    oppfolgingstilfelleArbeidstaker.tilfelleGenerert.shouldBeEqualToOffsetDateTime(
                        kafkaOppfolgingstilfelleArbeidstaker.createdAt
                    )
                    oppfolgingstilfelleArbeidstaker.tilfelleStart shouldBeEqualTo latestTilfelle.start
                    oppfolgingstilfelleArbeidstaker.tilfelleEnd shouldBeEqualTo latestTilfelle.end
                    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid
                    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet.shouldBeEqualToOffsetDateTime(
                        kafkaOppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet
                    )
                }
            }
        }
    }
})
