package no.nav.syfo.oppfolgingstilfelle.kafka

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.domain.ARENA_CUTOFF
import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfelle
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.OPPFOLGINGSTILFELLE_PERSON_TOPIC
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_DOD
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateKafkaOppfolgingstilfellePerson
import org.junit.jupiter.api.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals

class KafkaOppfolgingstilfellePersonServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private lateinit var kafkaConsumer: KafkaConsumer<String, KafkaOppfolgingstilfellePerson>
    private lateinit var service: KafkaOppfolgingstilfellePersonService

    private val partition = 0
    private val topicPartition = TopicPartition(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition)

    @BeforeEach
    fun setup() {
        database.dropData()
        kafkaConsumer = mockk(relaxed = true)
        service = KafkaOppfolgingstilfellePersonService(database = database)
        clearMocks(kafkaConsumer)
        every { kafkaConsumer.commitSync() } returns Unit
    }

    private fun assertPlanlagt(
        stoppunkt: DialogmotekandidatStoppunkt?,
        person: KafkaOppfolgingstilfellePerson,
    ) {
        assertNotNull(stoppunkt)
        val latestTilfelleStart = person.oppfolgingstilfelleList.maxByOrNull { it.start }!!.start
        val expectedPlanlagt = latestTilfelleStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        assertEquals(person.personIdentNumber, stoppunkt!!.personIdent.value)
        assertNull(stoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, stoppunkt.status)
        assertEquals(expectedPlanlagt, stoppunkt.stoppunktPlanlagt)
    }

    @Test
    fun `should create DialogmotekandidatStoppunkt for both current and future tilfelle`() {
        val currentPerson = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
        )
        val futurePerson = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            start = LocalDate.now().plusDays(130),
            oppfolgingstilfelleDurationInDays = 120,
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", currentPerson),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 2L, "key2", futurePerson),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(currentPerson.personIdentNumber))
            .toDialogmotekandidatStoppunktList()
        assertEquals(2, stoppunktList.size)
        assertPlanlagt(stoppunktList[0], futurePerson)
        assertPlanlagt(stoppunktList[1], currentPerson)
    }

    @Test
    fun `should create DialogmotekandidatStoppunkt for both current and future tilfelle if included in same Kafka-record`() {
        val combinedPerson = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = 120,
        ).copy(
            oppfolgingstilfelleList = listOf(
                KafkaOppfolgingstilfelle(
                    arbeidstakerAtTilfelleEnd = true,
                    start = LocalDate.now().minusDays(130),
                    end = LocalDate.now(),
                    virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                ),
                KafkaOppfolgingstilfelle(
                    arbeidstakerAtTilfelleEnd = true,
                    start = LocalDate.now().plusDays(120),
                    end = LocalDate.now().plusDays(250),
                    virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                ),
            ),
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", combinedPerson),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(combinedPerson.personIdentNumber))
            .toDialogmotekandidatStoppunktList()
        assertEquals(2, stoppunktList.size)
        assertPlanlagt(stoppunktList[0], combinedPerson)
        val currentTilfelle = combinedPerson.oppfolgingstilfelleList[0]
        val stoppunktCurrent = stoppunktList[1]
        assertEquals(combinedPerson.personIdentNumber, stoppunktCurrent.personIdent.value)
        assertNull(stoppunktCurrent.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, stoppunktCurrent.status)
        assertEquals(currentTilfelle.start.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS), stoppunktCurrent.stoppunktPlanlagt)
    }

    @Test
    fun `should create 2 DialogmotekandidatStoppunkt, if polled 1 that is not Dialogmotekandidat and 2 that are Dialogmotekandidat`() {
        val first = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
        )
        val notCandidate = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
        )
        val last = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", first),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 2L, "key2", notCandidate),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 3L, "key3", last),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(first.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(2, stoppunktList.size)
        assertPlanlagt(stoppunktList.first(), last)
        assertPlanlagt(stoppunktList.last(), first)
    }

    @Test
    fun `should not generate stoppunkt back in time for current oppfolgingstilfelle`() {
        ARENA_CUTOFF = LocalDate.of(2022, 1, 1)
        val tilbakedatert = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            start = LocalDate.now().minusDays(110),
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
        )
        val tilbakedatertLast = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            start = LocalDate.now().minusDays(130),
            oppfolgingstilfelleDurationInDays = 140,
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", tilbakedatert),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 2L, "key2", tilbakedatertLast),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(tilbakedatertLast.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(1, stoppunktList.size)
        val stoppunkt = stoppunktList.first()
        assertNotNull(stoppunkt)
        assertEquals(tilbakedatertLast.personIdentNumber, stoppunkt.personIdent.value)
        assertNull(stoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, stoppunkt.status)
        assertEquals(LocalDate.now(), stoppunkt.stoppunktPlanlagt)
    }

    @Test
    fun `should generate stoppunkt back in time for oppfolgingstilfelle that started before ARENA_CUTOFF`() {
        val tilbakedatertLastStart = LocalDate.now().minusDays(130)
        val tilbakedatert = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            start = LocalDate.now().minusDays(110),
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
        )
        val tilbakedatertLast = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            start = tilbakedatertLastStart,
            oppfolgingstilfelleDurationInDays = 140,
        )
        ARENA_CUTOFF = tilbakedatertLastStart.plusDays(1)
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", tilbakedatert),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 2L, "key2", tilbakedatertLast),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(tilbakedatertLast.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(1, stoppunktList.size)
        val stoppunkt = stoppunktList.first()
        assertNotNull(stoppunkt)
        val expectedPlanlagt = tilbakedatertLastStart.plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS)
        assertEquals(tilbakedatertLast.personIdentNumber, stoppunkt.personIdent.value)
        assertNull(stoppunkt.processedAt)
        assertEquals(DialogmotekandidatStoppunktStatus.PLANLAGT_KANDIDAT, stoppunkt.status)
        assertEquals(expectedPlanlagt, stoppunkt.stoppunktPlanlagt)
    }

    @Test
    fun `should not create DialogmotekandidatStoppunkt if polled record is dialogmotekandidat but not Arbeidstaker at end of tilfelle`() {
        val notArbeidstaker = generateKafkaOppfolgingstilfellePerson(
            arbeidstakerAtTilfelleEnd = false,
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", notArbeidstaker),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(ARBEIDSTAKER_PERSONIDENTNUMBER.value)).toDialogmotekandidatStoppunktList()
        assertEquals(0, stoppunktList.size)
    }

    @Test
    fun `should not create DialogmotekandidatStoppunkt if dialogmotekandidat has dodsdato`() {
        val withDodsdato = generateKafkaOppfolgingstilfellePerson(
            arbeidstakerAtTilfelleEnd = true,
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER_DOD,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
            dodsdato = LocalDate.now(),
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", withDodsdato),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(withDodsdato.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(0, stoppunktList.size)
    }

    @Test
    fun `should not create stoppunkt when previous oppfolgingstilfelle was kandidat but latest is not kandidat and Arbeidstaker at end`() {
        val previousOnly = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        ).copy(
            oppfolgingstilfelleList = listOf(
                KafkaOppfolgingstilfelle(
                    arbeidstakerAtTilfelleEnd = true,
                    start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 31),
                    end = LocalDate.now().minusDays(30),
                    virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                ),
            ),
        )
        val previousAndLatest = previousOnly.copy(
            oppfolgingstilfelleList = previousOnly.oppfolgingstilfelleList + KafkaOppfolgingstilfelle(
                arbeidstakerAtTilfelleEnd = true,
                start = previousOnly.oppfolgingstilfelleList.first().end.plusDays(17),
                end = previousOnly.oppfolgingstilfelleList.first().end.plusDays(27),
                virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
            ),
            referanseTilfelleBitInntruffet = previousOnly.referanseTilfelleBitInntruffet.plusSeconds(1)
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", previousOnly),
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 2L, "key2", previousAndLatest),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(previousOnly.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(1, stoppunktList.size)
        assertPlanlagt(stoppunktList.first(), previousOnly)
    }

    @Test
    fun `should not create stoppunkt when previous oppfolgingstilfelle was kandidat but latest is not kandidat and Arbeidstaker at end is false`() {
        val notArbeidstakerLatest = generateKafkaOppfolgingstilfellePerson(
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        ).copy(
            oppfolgingstilfelleList = listOf(
                KafkaOppfolgingstilfelle(
                    arbeidstakerAtTilfelleEnd = false,
                    start = LocalDate.now().minusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS),
                    end = LocalDate.now().plusDays(1),
                    virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                ),
                KafkaOppfolgingstilfelle(
                    arbeidstakerAtTilfelleEnd = true,
                    start = LocalDate.now().plusDays(17),
                    end = LocalDate.now().plusDays(27),
                    virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                ),
            ),
        )
        val records = ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    ConsumerRecord(OPPFOLGINGSTILFELLE_PERSON_TOPIC, partition, 1L, "key1", notArbeidstakerLatest),
                )
            )
        )
        every { kafkaConsumer.poll(any<Duration>()) } returns records

        service.pollAndProcessRecords(kafkaConsumerOppfolgingstilfellePerson = kafkaConsumer)

        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val stoppunktList = database.getDialogmotekandidatStoppunktList(Personident(notArbeidstakerLatest.personIdentNumber)).toDialogmotekandidatStoppunktList()
        assertEquals(0, stoppunktList.size)
    }
}
