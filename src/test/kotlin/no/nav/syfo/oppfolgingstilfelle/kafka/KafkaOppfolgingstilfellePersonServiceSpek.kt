package no.nav.syfo.oppfolgingstilfelle.kafka

import io.mockk.*
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatStoppunktList
import no.nav.syfo.domain.ARENA_CUTOFF
import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfelle
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.OPPFOLGINGSTILFELLE_PERSON_TOPIC
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD
import no.nav.syfo.testhelper.generator.generateKafkaOppfolgingstilfellePerson
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDate

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({

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
    val kafkaOppfolgingstilfellePersonNotDialogmotekandidat = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
    )
    val kafkaOppfolgingstilfellePersonNotDialogmotekandidatRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        2,
        "key2",
        kafkaOppfolgingstilfellePersonNotDialogmotekandidat,
    )
    val kafkaOppfolgingstilfellePersonTilbakedatert = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().minusDays(110),
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
    )
    val kafkaOppfolgingstilfellePersonTilbakedatertRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        3,
        "key3",
        kafkaOppfolgingstilfellePersonTilbakedatert,
    )
    val kafkaOppfolgingstilfellePersonDialogmotekandidatLast = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
    )
    val kafkaOppfolgingstilfellePersonDialogmotekandidatLastRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        4,
        "key4",
        kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
    )
    val kafkaOppfolgingstilfellePersonTilbakedatertLast = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().minusDays(130),
        oppfolgingstilfelleDurationInDays = 140,
    )
    val kafkaOppfolgingstilfellePersonTilbakedatertLastRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        5,
        "key5",
        kafkaOppfolgingstilfellePersonTilbakedatertLast,
    )
    val kafkaOppfolgingstilfellePersonFramtidig = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().plusDays(130),
        oppfolgingstilfelleDurationInDays = 120,
    )
    val kafkaOppfolgingstilfellePersonFramtidigRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        6,
        "key6",
        kafkaOppfolgingstilfellePersonFramtidig,
    )
    val kafkaOppfolgingstilfellePersonVanligOgFramtidig = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = 120,
    ).copy(
        oppfolgingstilfelleList = listOf(
            KafkaOppfolgingstilfelle(
                arbeidstakerAtTilfelleEnd = true,
                start = LocalDate.now().minusDays(130),
                end = LocalDate.now(),
                virksomhetsnummerList = listOf(
                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                )
            ),
            KafkaOppfolgingstilfelle(
                arbeidstakerAtTilfelleEnd = true,
                start = LocalDate.now().plusDays(120),
                end = LocalDate.now().plusDays(250),
                virksomhetsnummerList = listOf(
                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                )
            ),
        ),
    )
    val kafkaOppfolgingstilfellePersonVanligOgFramtidigRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        7,
        "key7",
        kafkaOppfolgingstilfellePersonVanligOgFramtidig,
    )

    val mockKafkaConsumerOppfolgingstilfellePerson =
        mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

    describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: Poll and proceess reccords") {
        describe("Receive records, store and retrieve oppfolgingstilfelleArbeidstaker for PersonIdent") {

            beforeEachTest {
                clearMocks(mockKafkaConsumerOppfolgingstilfellePerson)
                every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
            }

            it("should create DialogmotekandidatStoppunkt for both current and future tilfelle") {
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecord,
                            kafkaOppfolgingstilfellePersonFramtidigRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 2

                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[0],
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonFramtidig,
                )
                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[1],
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
                )
            }

            it("should create DialogmotekandidatStoppunkt for both current and future tilfelle if included in same Kafka-record") {
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonVanligOgFramtidigRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonVanligOgFramtidig.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 2

                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[0],
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonVanligOgFramtidig,
                )
                val currentTilfelle = kafkaOppfolgingstilfellePersonVanligOgFramtidig.oppfolgingstilfelleList[0]
                val stoppunktForCurrentTilfelle = dialogmotekandidatStoppunktList[1]
                stoppunktForCurrentTilfelle.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                stoppunktForCurrentTilfelle.processedAt.shouldBeNull()
                stoppunktForCurrentTilfelle.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT
                stoppunktForCurrentTilfelle.stoppunktPlanlagt shouldBeEqualTo currentTilfelle.start.plusDays(
                    DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
                )
            }

            it("should create 2 DialogmotekandidatStoppunkt, if polled 1 that is not Dialogmotekandidat and 2 that are Dialogmotekandidat") {
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

                val dialogmotekandidatStoppunktList: List<DialogmotekandidatStoppunkt> =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 2

                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
                )

                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.last(),
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
                )
            }

            it("should not generate stoppunkt back in time for current oppfolgingstilfelle") {
                ARENA_CUTOFF = LocalDate.of(2022, 1, 1)
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonTilbakedatertRecord,
                            kafkaOppfolgingstilfellePersonTilbakedatertLastRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList: List<DialogmotekandidatStoppunkt> =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 1
                val dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first()
                dialogmotekandidatStoppunkt.shouldNotBeNull()

                val stoppunktPlanlagtExpected = LocalDate.now()

                dialogmotekandidatStoppunkt.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber
                dialogmotekandidatStoppunkt.processedAt.shouldBeNull()
                dialogmotekandidatStoppunkt.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT
                dialogmotekandidatStoppunkt.stoppunktPlanlagt shouldBeEqualTo stoppunktPlanlagtExpected
            }

            it("should generate stoppunkt back in time for oppfolgingstilfelle that started before ARENA_CUTOFF") {
                ARENA_CUTOFF = kafkaOppfolgingstilfellePersonTilbakedatertLast.oppfolgingstilfelleList.first().start.plusDays(1)
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonTilbakedatertRecord,
                            kafkaOppfolgingstilfellePersonTilbakedatertLastRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList: List<DialogmotekandidatStoppunkt> =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 1
                val dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first()
                dialogmotekandidatStoppunkt.shouldNotBeNull()

                val stoppunktPlanlagtExpected = kafkaOppfolgingstilfellePersonTilbakedatertLast.oppfolgingstilfelleList
                    .first().start.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

                dialogmotekandidatStoppunkt.personIdent.value shouldBeEqualTo kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber
                dialogmotekandidatStoppunkt.processedAt.shouldBeNull()
                dialogmotekandidatStoppunkt.status shouldBeEqualTo DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT
                dialogmotekandidatStoppunkt.stoppunktPlanlagt shouldBeEqualTo stoppunktPlanlagtExpected
            }

            it("should not create DialogmotekandidatStoppunkt, if polled 1 that is Dialogmotekandidat, but is not Arbeidstaker at end of tilfelle ") {
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
                val dialogmotekandidatStoppunktList =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 0
            }
            it("should not create DialogmotekandidatStoppunkt, if polled 1 that is Dialogmotekandidat with dodsdato != null ") {
                val kafkaOppfolgingstilfellePersonDialogmotekandidatWithDodsdato =
                    generateKafkaOppfolgingstilfellePerson(
                        arbeidstakerAtTilfelleEnd = true,
                        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER_DOD,
                        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                        dodsdato = LocalDate.now(),
                    )
                val kafkaOppfolgingstilfellePersonDialogmotekandidatWithDodsdatoRecord =
                    ConsumerRecord(
                        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
                        partition,
                        4,
                        "key4",
                        kafkaOppfolgingstilfellePersonDialogmotekandidatWithDodsdato,
                    )

                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatWithDodsdatoRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }
                val dialogmotekandidatStoppunktList =
                    database.getDialogmotekandidatStoppunktList(
                        arbeidstakerPersonIdent = Personident(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatWithDodsdato.personIdentNumber
                        )
                    ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 0
            }

            it("should not create DialogmotekandidatStoppunkt, if polled 1 where arbeidstakerAtTilfelleEnd=true and Dialogmotekandidat=true in previous Oppfolgingstilfelle, and Dialogmotekandidat=false in latest Oppfolgingstilfelle") {
                val kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle =
                    generateKafkaOppfolgingstilfellePerson(
                        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
                        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                    ).copy(
                        oppfolgingstilfelleList = listOf(
                            KafkaOppfolgingstilfelle(
                                arbeidstakerAtTilfelleEnd = true,
                                start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 31),
                                end = LocalDate.now().minusDays(30),
                                virksomhetsnummerList = listOf(
                                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                                ),
                            ),
                        ),
                    )
                val kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelleRecord =
                    ConsumerRecord(
                        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
                        partition,
                        1,
                        "key1",
                        kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle,
                    )

                val kafkaOppfolgingstilfellePersonDialogmotekandidatPreviousOppfolgingstilfelle =
                    generateKafkaOppfolgingstilfellePerson(
                        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
                        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                    ).copy(
                        oppfolgingstilfelleList = listOf(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle.oppfolgingstilfelleList.first(),
                            KafkaOppfolgingstilfelle(
                                arbeidstakerAtTilfelleEnd = true,
                                start = kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle.oppfolgingstilfelleList.first().end.plusDays(
                                    17
                                ),
                                end = kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle.oppfolgingstilfelleList.first().end.plusDays(
                                    27
                                ),
                                virksomhetsnummerList = listOf(
                                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                                ),
                            ),
                        ),
                        referanseTilfelleBitInntruffet = kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle.referanseTilfelleBitInntruffet.plusSeconds(
                            1
                        )
                    )
                val kafkaOppfolgingstilfellePersonDialogmotekandidatPreviousOppfolgingstilfelleRecord =
                    ConsumerRecord(
                        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
                        partition,
                        2,
                        "key2",
                        kafkaOppfolgingstilfellePersonDialogmotekandidatPreviousOppfolgingstilfelle,
                    )

                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelleRecord,
                            kafkaOppfolgingstilfellePersonDialogmotekandidatPreviousOppfolgingstilfelleRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList = database.getDialogmotekandidatStoppunktList(
                    arbeidstakerPersonIdent = Personident(
                        kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber,
                    ),
                ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 1

                assertDialogmotekandidatStoppunktPlanlagt(
                    dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
                    kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle,
                )
            }

            it("should not create DialogmotekandidatStoppunkt, if polled 1 where arbeidstakerAtTilfelleEnd=false and Dialogmotekandidat=true in previous Oppfolgingstilfelle, and Dialogmotekandidat=false in latest Oppfolgingstilfelle") {
                val kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerPreviousOppfolgingstilfelle =
                    generateKafkaOppfolgingstilfellePerson(
                        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
                        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
                    ).copy(
                        oppfolgingstilfelleList = listOf(
                            KafkaOppfolgingstilfelle(
                                arbeidstakerAtTilfelleEnd = false,
                                start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
                                end = LocalDate.now().plusDays(1),
                                virksomhetsnummerList = listOf(
                                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                                ),
                            ),
                            KafkaOppfolgingstilfelle(
                                arbeidstakerAtTilfelleEnd = true,
                                start = LocalDate.now().plusDays(1).plusDays(16),
                                end = LocalDate.now().plusDays(1).plusDays(20),
                                virksomhetsnummerList = listOf(
                                    UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value,
                                ),
                            ),
                        ),
                    )
                val kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerPreviousOppfolgingstilfelleRecord =
                    ConsumerRecord(
                        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
                        partition,
                        1,
                        "key1",
                        kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerPreviousOppfolgingstilfelle,
                    )

                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        oppfolgingstilfelleArbeidstakerTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonDialogmotekandidatNotArbeidstakerPreviousOppfolgingstilfelleRecord,
                        )
                    )
                )

                kafkaSyketilfellebitService.pollAndProcessRecords(
                    kafkaConsumerOppfolgingstilfellePerson = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val dialogmotekandidatStoppunktList = database.getDialogmotekandidatStoppunktList(
                    arbeidstakerPersonIdent = Personident(
                        kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber,
                    ),
                ).toDialogmotekandidatStoppunktList()

                dialogmotekandidatStoppunktList.size shouldBeEqualTo 0
            }
        }
    }
})
