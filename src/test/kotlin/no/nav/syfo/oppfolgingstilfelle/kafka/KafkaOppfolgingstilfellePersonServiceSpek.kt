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
import no.nav.syfo.testhelper.generator.generateKafkaOppfolgingstilfellePerson
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        beforeEachTest {
            database.dropData()
        }

        val kafkaSyketilfellebitService = KafkaOppfolgingstilfellePersonService(
            database = database,
        )

        val partition = 0
        val oppfolgingstilfelleArbeidstakerTopicPartition = TopicPartition(
            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
            partition,
        )

        val kafkaOppfolgingstilfellePersonDialogmotekandidatFirst = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
        )
        val kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
            partition,
            1,
            "key1",
            kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
        )
        val kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecordDuplicate = ConsumerRecord(
            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
            partition,
            1,
            "key1",
            kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
        )
        val kafkaOppfolgingstilfellePersonNotDialogmotekandidat = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1
        )
        val kafkaOppfolgingstilfellePersonNotDialogmotekandidatRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
            partition,
            2,
            "key2",
            kafkaOppfolgingstilfellePersonNotDialogmotekandidat,
        )
        val kafkaOppfolgingstilfellePersonDialogmotekandidatLast = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1
        )
        val kafkaOppfolgingstilfellePersonDialogmotekandidatLastRecord = ConsumerRecord(
            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
            partition,
            3,
            "key3",
            kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
        )

        val mockKafkaConsumerOppfolgingstilfellePerson =
            mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: Poll and proceess reccords") {
            describe("Receive records, store and retrieve oppfolgingstilfelleArbeidstaker for PersonIdent") {

                beforeEachTest {
                    clearMocks(mockKafkaConsumerOppfolgingstilfellePerson)
                    every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
                }

                it("should create OppfolgingstilfelleArbeidstaker exactly once if Dialogmotekandidat and not already created(skip duplicates)") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecord,
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecordDuplicate,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstakerList: List<OppfolgingstilfelleArbeidstaker?> =
                        database.getOppfolgingstilfelleArbeidstakerList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toOppfolgingstilfelleArbeidstakerList()

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 1

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.first(),
                        kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
                    )
                }

                it("should create 2 OppfolgingstilfelleArbeidstaker, if polled 1 that is not Dialogmotekandidat and 2 that are Dialogmotekandidat") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecord,
                                kafkaOppfolgingstilfellePersonNotDialogmotekandidatRecord,
                                kafkaOppfolgingstilfellePersonDialogmotekandidatLastRecord,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstakerList: List<OppfolgingstilfelleArbeidstaker?> =
                        database.getOppfolgingstilfelleArbeidstakerList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toOppfolgingstilfelleArbeidstakerList()

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 2

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.first(),
                        kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
                    )

                    assertOppfolgingstilfelleArbeidstaker(
                        oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstakerList.last(),
                        kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
                    )

                    val dialogmotekandidatStoppunktList: List<DialogmotekandidatStoppunkt> =
                        database.getDialogmotekandidatStoppunktList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toDialogmotekandidatStoppunktList()

                    assertDialogmotekandidatStoppunktPlanlagt(
                        dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
                        kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
                    )

                    assertDialogmotekandidatStoppunktPlanlagt(
                        dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.last(),
                        kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
                    )
                }

                it("should not create OppfolgingstilfellePerson, if polled 1 that is Dialogmotekandidat, but is not Arbeidstaker at end of tilfelle ") {
                    val kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerAtLatestTilfelle =
                        generateKafkaOppfolgingstilfellePerson(
                            arbeidstakerAtTilfelleEnd = false,
                            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
                            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
                        )
                    val kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerAtLatestTilfelleRecord =
                        ConsumerRecord(
                            OPPFOLGINGSTILFELLE_PERSON_TOPIC,
                            partition,
                            4,
                            "key4",
                            kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerAtLatestTilfelle,
                        )

                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerAtLatestTilfelleRecord,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val oppfolgingstilfelleArbeidstakerList: List<OppfolgingstilfelleArbeidstaker?> =
                        database.getOppfolgingstilfelleArbeidstakerList(
                            arbeidstakerPersonIdent = PersonIdentNumber(
                                kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                            )
                        ).toOppfolgingstilfelleArbeidstakerList()

                    oppfolgingstilfelleArbeidstakerList.size shouldBeEqualTo 0
                }
            }
        }
    }
})

fun assertOppfolgingstilfelleArbeidstaker(
    oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker?,
    kafkaOppfolgingstilfellePersonDialogmotekandidat: KafkaOppfolgingstilfellePerson,
) {
    oppfolgingstilfelleArbeidstaker.shouldNotBeNull()

    val latestTilfelle =
        kafkaOppfolgingstilfellePersonDialogmotekandidat.oppfolgingstilfelleList.maxByOrNull { it.start }
            ?: throw RuntimeException("No Oppfolgingstilfelle found")

    oppfolgingstilfelleArbeidstaker.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfellePersonDialogmotekandidat.personIdentNumber
    oppfolgingstilfelleArbeidstaker.tilfelleGenerert.shouldBeEqualToOffsetDateTime(
        kafkaOppfolgingstilfellePersonDialogmotekandidat.createdAt
    )
    oppfolgingstilfelleArbeidstaker.tilfelleStart shouldBeEqualTo latestTilfelle.start
    oppfolgingstilfelleArbeidstaker.tilfelleEnd shouldBeEqualTo latestTilfelle.end
    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfellePersonDialogmotekandidat.referanseTilfelleBitUuid
    oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet.shouldBeEqualToOffsetDateTime(
        kafkaOppfolgingstilfellePersonDialogmotekandidat.referanseTilfelleBitInntruffet
    )
}

fun assertDialogmotekandidatStoppunktPlanlagt(
    dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt?,
    kafkaOppfolgingstilfellePersonDialogmotekandidat: KafkaOppfolgingstilfellePerson,
) {
    dialogmotekandidatStoppunkt.shouldNotBeNull()

    val latestTilfelleStart =
        kafkaOppfolgingstilfellePersonDialogmotekandidat.oppfolgingstilfelleList.maxByOrNull {
            it.start
        }!!.start
    val stoppunktPlanlagtExpected = latestTilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

    dialogmotekandidatStoppunkt.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfellePersonDialogmotekandidat.personIdentNumber
    dialogmotekandidatStoppunkt.processedAt.shouldBeNull()
    dialogmotekandidatStoppunkt.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT
    dialogmotekandidatStoppunkt.stoppunktPlanlagt shouldBeEqualTo stoppunktPlanlagtExpected
}
