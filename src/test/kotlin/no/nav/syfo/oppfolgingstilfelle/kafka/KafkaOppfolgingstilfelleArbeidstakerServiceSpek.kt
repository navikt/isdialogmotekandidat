package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstakerList
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

        val kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst = generateKafkaOppfolgingstilfelleArbeidstaker(
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
        )
        val kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirstRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
            1,
            "key1",
            kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst,
        )
        val kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirstRecordDuplicate = ConsumerRecord(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
            1,
            "key1",
            kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst,
        )
        val kafkaOppfolgingstilfelleArbeidstakerNotDialogmotekandidat = generateKafkaOppfolgingstilfelleArbeidstaker(
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1
        )
        val kafkaOppfolgingstilfelleArbeidstakerNotDialogmotekandidatRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
            2,
            "key2",
            kafkaOppfolgingstilfelleArbeidstakerNotDialogmotekandidat,
        )
        val kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLast = generateKafkaOppfolgingstilfelleArbeidstaker(
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1
        )
        val kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLastRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_ARBEIDSTAKER_TOPIC,
            partition,
            3,
            "key3",
            kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLast,
        )

        val mockKafkaConsumerOppfolgingstilfelleArbeidstaker =
            mockk<KafkaConsumer<String, KafkaOppfolgingstilfelleArbeidstaker>>()

        describe("${KafkaOppfolgingstilfelleArbeidstakerService::class.java.simpleName}: Poll and proceess reccords") {
            describe("Receive records, store and retrieve oppfolgingstilfelleArbeidstaker for PersonIdent") {

                beforeEachTest {
                    clearMocks(mockKafkaConsumerOppfolgingstilfelleArbeidstaker)
                    every { mockKafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync() } returns Unit
                }

                it("should create OppfolgingstilfelleArbeidstaker exactly once if Dialogmotekandidat and not already created(skip duplicates)") {
                    every { mockKafkaConsumerOppfolgingstilfelleArbeidstaker.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirstRecord,
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirstRecordDuplicate,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfelleArbeidstaker = mockKafkaConsumerOppfolgingstilfelleArbeidstaker,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstakerList: List<OppfolgingstilfelleArbeidstaker?> =
                        database.connection.use { connection ->
                            connection.getOppfolgingstilfelleArbeidstakerList(
                                arbeidstakerPersonIdent = PersonIdentNumber(
                                    kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst.personIdentNumber
                                )
                            ).toOppfolgingstilfelleArbeidstakerList()
                        }

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 1

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.first(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst,
                    )
                }

                it("should create 2 OppfolgingstilfelleArbeidstaker, if polled 1 that is not Dialogmotekandidat and 2 that are Dialogmotekandidat") {
                    every { mockKafkaConsumerOppfolgingstilfelleArbeidstaker.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirstRecord,
                                kafkaOppfolgingstilfelleArbeidstakerNotDialogmotekandidatRecord,
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLastRecord,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfelleArbeidstaker = mockKafkaConsumerOppfolgingstilfelleArbeidstaker,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfelleArbeidstaker.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstakerList: List<OppfolgingstilfelleArbeidstaker?> =
                        database.connection.use { connection ->
                            connection.getOppfolgingstilfelleArbeidstakerList(
                                arbeidstakerPersonIdent = PersonIdentNumber(
                                    kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst.personIdentNumber
                                )
                            ).toOppfolgingstilfelleArbeidstakerList()
                        }

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 2

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.first(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLast,
                    )

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.last(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst,
                    )
                }
            }
        }
    }
})

fun assertOppfolgingstilfelleArbeidstaker(
    oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker?,
    kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat: KafkaOppfolgingstilfelleArbeidstaker,
) {
    oppfolgingstilfelleArbeidstaker.shouldNotBeNull()

    val latestTilfelle =
        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.oppfolgingstilfelleList.maxByOrNull { it.start }
            ?: throw RuntimeException("No Oppfolgingstilfelle found")

    oppfolgingstilfelleArbeidstaker.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.personIdentNumber
    oppfolgingstilfelleArbeidstaker.tilfelleGenerert.shouldBeEqualToOffsetDateTime(
        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.createdAt
    )
    oppfolgingstilfelleArbeidstaker.tilfelleStart shouldBeEqualTo latestTilfelle.start
    oppfolgingstilfelleArbeidstaker.tilfelleEnd shouldBeEqualTo latestTilfelle.end
    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.referanseTilfelleBitUuid
    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet.shouldBeEqualToOffsetDateTime(
        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.referanseTilfelleBitInntruffet
    )
}
