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
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class KafkaOppfolgingstilfellePersonServiceTest {

    private fun assertDialogmotekandidatStoppunktPlanlagt(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt?,
        kafkaOppfolgingstilfellePersonDialogmotekandidat: KafkaOppfolgingstilfellePerson,
    ) {
        assertNotNull(dialogmotekandidatStoppunkt)

        val latestTilfelleStart =
            kafkaOppfolgingstilfellePersonDialogmotekandidat.oppfolgingstilfelleList.maxByOrNull {
                it.start
            }!!.start
        val stoppunktPlanlagtExpected = latestTilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

        assertEquals(kafkaOppfolgingstilfellePersonDialogmotekandidat.personIdentNumber, dialogmotekandidatStoppunkt!!.personIdent.value)
        assertNull(dialogmotekandidatStoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, dialogmotekandidatStoppunkt.status)
        assertEquals(stoppunktPlanlagtExpected, dialogmotekandidatStoppunkt.stoppunktPlanlagt)
    }

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    private val kafkaSyketilfellebitService = KafkaOppfolgingstilfellePersonService(
        database = database,
    )

    private val partition = 0
    private val oppfolgingstilfelleArbeidstakerTopicPartition = TopicPartition(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
    )

    private val kafkaOppfolgingstilfellePersonDialogmotekandidatFirst = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
    )
    private val kafkaOppfolgingstilfellePersonDialogmotekandidatFirstRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        1,
        "key1",
        kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
    )
    private val kafkaOppfolgingstilfellePersonNotDialogmotekandidat = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
    )
    private val kafkaOppfolgingstilfellePersonNotDialogmotekandidatRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        2,
        "key2",
        kafkaOppfolgingstilfellePersonNotDialogmotekandidat,
    )
    private val kafkaOppfolgingstilfellePersonTilbakedatert = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().minusDays(110),
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
    )
    private val kafkaOppfolgingstilfellePersonTilbakedatertRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        3,
        "key3",
        kafkaOppfolgingstilfellePersonTilbakedatert,
    )
    private val kafkaOppfolgingstilfellePersonDialogmotekandidatLast = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
    )
    private val kafkaOppfolgingstilfellePersonDialogmotekandidatLastRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        4,
        "key4",
        kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
    )
    private val kafkaOppfolgingstilfellePersonTilbakedatertLast = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().minusDays(130),
        oppfolgingstilfelleDurationInDays = 140,
    )
    private val kafkaOppfolgingstilfellePersonTilbakedatertLastRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        5,
        "key5",
        kafkaOppfolgingstilfellePersonTilbakedatertLast,
    )
    private val kafkaOppfolgingstilfellePersonFramtidig = generateKafkaOppfolgingstilfellePerson(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        start = LocalDate.now().plusDays(130),
        oppfolgingstilfelleDurationInDays = 120,
    )
    private val kafkaOppfolgingstilfellePersonFramtidigRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        6,
        "key6",
        kafkaOppfolgingstilfellePersonFramtidig,
    )
    private val kafkaOppfolgingstilfellePersonVanligOgFramtidig = generateKafkaOppfolgingstilfellePerson(
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
    private val kafkaOppfolgingstilfellePersonVanligOgFramtidigRecord = ConsumerRecord(
        OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        partition,
        7,
        "key7",
        kafkaOppfolgingstilfellePersonVanligOgFramtidig,
    )

    private val mockKafkaConsumerOppfolgingstilfellePerson =
        mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

    private fun setupMockConsumer() {
        clearMocks(mockKafkaConsumerOppfolgingstilfellePerson)
        every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
    }

    @Test
    fun `should create DialogmotekandidatStoppunkt for both current and future tilfelle`() {
        setupMockConsumer()
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

        assertEquals(2, dialogmotekandidatStoppunktList.size)

        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[0],
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonFramtidig,
        )
        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[1],
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
        )
    }

    @Test
    fun `should create DialogmotekandidatStoppunkt for both current and future tilfelle if included in same Kafka-record`() {
        setupMockConsumer()
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

        assertEquals(2, dialogmotekandidatStoppunktList.size)

        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList[0],
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonVanligOgFramtidig,
        )
        val currentTilfelle = kafkaOppfolgingstilfellePersonVanligOgFramtidig.oppfolgingstilfelleList[0]
        val stoppunktForCurrentTilfelle = dialogmotekandidatStoppunktList[1]
        assertEquals(kafkaOppfolgingstilfellePersonDialogmotekandidatFirst.personIdentNumber, stoppunktForCurrentTilfelle.personIdent.value)
        assertNull(stoppunktForCurrentTilfelle.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, stoppunktForCurrentTilfelle.status)
        assertEquals(currentTilfelle.start.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), stoppunktForCurrentTilfelle.stoppunktPlanlagt)
    }

    @Test
    fun `should create 2 DialogmotekandidatStoppunkt, if polled 1 that is not Dialogmotekandidat and 2 that are Dialogmotekandidat`() {
        setupMockConsumer()
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

        assertEquals(2, dialogmotekandidatStoppunktList.size)

        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLast,
        )

        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.last(),
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatFirst,
        )
    }

    @Test
    fun `should not generate stoppunkt back in time for current oppfolgingstilfelle`() {
        setupMockConsumer()
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

        assertEquals(1, dialogmotekandidatStoppunktList.size)
        val dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first()
        assertNotNull(dialogmotekandidatStoppunkt)

        val stoppunktPlanlagtExpected = LocalDate.now()

        assertEquals(kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber, dialogmotekandidatStoppunkt.personIdent.value)
        assertNull(dialogmotekandidatStoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, dialogmotekandidatStoppunkt.status)
        assertEquals(stoppunktPlanlagtExpected, dialogmotekandidatStoppunkt.stoppunktPlanlagt)
    }

    @Test
    fun `should generate stoppunkt back in time for oppfolgingstilfelle that started before ARENA_CUTOFF`() {
        setupMockConsumer()
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

        assertEquals(1, dialogmotekandidatStoppunktList.size)
        val dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first()
        assertNotNull(dialogmotekandidatStoppunkt)

        val stoppunktPlanlagtExpected = kafkaOppfolgingstilfellePersonTilbakedatertLast.oppfolgingstilfelleList
            .first().start.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)

        assertEquals(kafkaOppfolgingstilfellePersonTilbakedatertLast.personIdentNumber, dialogmotekandidatStoppunkt.personIdent.value)
        assertNull(dialogmotekandidatStoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, dialogmotekandidatStoppunkt.status)
        assertEquals(stoppunktPlanlagtExpected, dialogmotekandidatStoppunkt.stoppunktPlanlagt)
    }

    @Test
    fun `should not create DialogmotekandidatStoppunkt, if polled 1 that is Dialogmotekandidat, but is not Arbeidstaker at end of tilfelle`() {
        setupMockConsumer()
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

        assertEquals(0, dialogmotekandidatStoppunktList.size)
    }

    @Test
    fun `should not create DialogmotekandidatStoppunkt, if polled 1 that is Dialogmotekandidat with dodsdato != null`() {
        setupMockConsumer()
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

        assertEquals(0, dialogmotekandidatStoppunktList.size)
    }

    @Test
    fun `should not create stoppunkt when person not kandidat in latest oppfolgingstilfelle`() {
        setupMockConsumer()
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

        assertEquals(1, dialogmotekandidatStoppunktList.size)

        assertDialogmotekandidatStoppunktPlanlagt(
            dialogmotekandidatStoppunkt = dialogmotekandidatStoppunktList.first(),
            kafkaOppfolgingstilfellePersonDialogmotekandidat = kafkaOppfolgingstilfellePersonDialogmotekandidatLatestOppfolgingstilfelle,
        )
    }

    @Test
    fun `should not create stoppunkt when arbeidstaker not at tilfelle end and not kandidat in latest tilfelle`() {
        setupMockConsumer()
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

        assertEquals(0, dialogmotekandidatStoppunktList.size)
    }
}
