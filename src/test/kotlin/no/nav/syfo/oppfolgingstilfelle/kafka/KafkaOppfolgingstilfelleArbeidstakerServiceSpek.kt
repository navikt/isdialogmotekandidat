package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.*
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatStoppunktList
import no.nav.syfo.dialogmotekandidat.database.toDialogmotekandidatStoppunktList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.generateKafkaOppfolgingstilfelleArbeidstaker
import org.amshove.kluent.*
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

        beforeEachTest {
            database.dropData()
        }

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
                        database.getOppfolgingstilfelleArbeidstakerList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toOppfolgingstilfelleArbeidstakerList()

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
                        database.getOppfolgingstilfelleArbeidstakerList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toOppfolgingstilfelleArbeidstakerList()

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 2

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.first(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLast,
                    )

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.last(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst,
                    )

                    val dialogmotekandidatStoppunktList: List<DialogmotekandidatStoppunkt> =
                        database.getDialogmotekandidatStoppunktList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toDialogmotekandidatStoppunktList()

                    assertDialogmotekandidatStoppunktPlanlagt(
                        dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
                        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat = kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidatLast,
                    )

                    assertDialogmotekandidatStoppunktPlanlagt(
                        dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.last(),
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

fun assertDialogmotekandidatStoppunktPlanlagt(
    dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt?,
    kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat: KafkaOppfolgingstilfelleArbeidstaker,
) {
    dialogmotekandidatStoppunkt.shouldNotBeNull()

    val latestTilfelleStart =
        kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.oppfolgingstilfelleList.maxByOrNull {
            it.start
        }!!.start
    val stoppunktPlanlagtExpected = latestTilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

    dialogmotekandidatStoppunkt.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfelleArbeidstakerDialogmotekandidat.personIdentNumber
    dialogmotekandidatStoppunkt.processedAt.shouldBeNull()
    dialogmotekandidatStoppunkt.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT
    dialogmotekandidatStoppunkt.stoppunktPlanlagt shouldBeEqualTo stoppunktPlanlagtExpected
}
