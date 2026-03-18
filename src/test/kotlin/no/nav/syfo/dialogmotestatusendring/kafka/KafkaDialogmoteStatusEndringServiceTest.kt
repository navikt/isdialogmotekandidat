package no.nav.syfo.dialogmotestatusendring.kafka

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.DialogmotekandidatVurderingService
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.DialogmoteStatusEndring
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringRecord
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DIALOGMOTE_STATUS_ENDRING_TOPIC
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DialogmoteStatusEndringConsumer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.createDialogmotekandidatEndring
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateKDialogmoteStatusEndring
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.Future

class KafkaDialogmoteStatusEndringServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmotekandidatRepository = externalMockEnvironment.dialogmotekandidatRepository
    private val kafkaProducer = mockk<KafkaProducer<String, DialogmotekandidatEndringRecord>>()
    private val dialogmotekandidatEndringProducer =
        DialogmotekandidatEndringProducer(producer = kafkaProducer)
    private val oppfolgingstilfelleService = externalMockEnvironment.oppfolgingstilfelleService
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = dialogmotekandidatRepository,
    )
    private val dialogmotekandidatVurderingService =
        DialogmotekandidatVurderingService(
            database = database,
            dialogmotekandidatService = dialogmotekandidatService,
            dialogmotekandidatVurderingRepository = externalMockEnvironment.dialogmotekandidatVurderingRepository,
            dialogmotekandidatRepository = externalMockEnvironment.dialogmotekandidatRepository,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
        )
    private val dialogmoteStatusEndringConsumer = DialogmoteStatusEndringConsumer(
        database = database,
        dialogmotekandidatRepository = dialogmotekandidatRepository,
        dialogmotekandidatService = dialogmotekandidatService,
        dialogmotekandidatVurderingService = dialogmotekandidatVurderingService,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )

    private val partition = 0
    private val topicPartition = TopicPartition(DIALOGMOTE_STATUS_ENDRING_TOPIC, partition)
    private val consumer = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()

    private val moteTidspunkt = OffsetDateTime.now().minusDays(1)
    private val statusEndringTidspunkt = OffsetDateTime.now()
    private val dialogmotekandidatEndringCreatedBeforeStatusEndring =
        generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            .copy(createdAt = statusEndringTidspunkt.minusDays(1))
    private val kDialogmoteStatusEndringOldFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndring.Type.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt.minusYears(1),
        endringsTidspunkt = statusEndringTidspunkt.minusYears(1),
    )
    private val kDialogmoteStatusEndringInnkalt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndring.Type.INNKALT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = moteTidspunkt,
    )
    private val kDialogmoteStatusEndringFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndring.Type.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    private val kDialogmoteStatusEndringLukket = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndring.Type.LUKKET,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    private val dialogmotekandidatEndringCreatedAfterStatusEndring =
        generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER)
            .copy(createdAt = statusEndringTidspunkt.plusDays(1))

    private val kDialogmoteStatusEndringOldFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        1L,
        "key1",
        kDialogmoteStatusEndringOldFerdigstilt
    )
    private val kDialogmoteStatusEndringInnkaltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        2L,
        "key2",
        kDialogmoteStatusEndringInnkalt
    )
    private val kDialogmoteStatusEndringFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        3L,
        "key3",
        kDialogmoteStatusEndringFerdigstilt
    )
    private val kDialogmoteStatusEndringLukketRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        4L,
        "key4",
        kDialogmoteStatusEndringLukket
    )

    @BeforeEach
    fun setup() {
        database.dropData()
        clearMocks(kafkaProducer, consumer)
        coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        every { consumer.commitSync() } returns Unit
    }

    @Test
    fun `creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before ferdigstilling`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedBeforeStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringOldFerdigstiltRecord,
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringFerdigstiltRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        val slot = slot<ProducerRecord<String, DialogmotekandidatEndringRecord>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slot)) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
        val latest =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        assertFalse(latest.kandidat)
        assertEquals(DialogmotekandidatEndring.Arsak.DIALOGMOTE_FERDIGSTILT, latest.arsak)
        val kafkaValue = slot.captured.value()
        assertEquals(ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaValue.personIdentNumber)
        assertFalse(kafkaValue.kandidat)
        assertEquals(DialogmotekandidatEndring.Arsak.DIALOGMOTE_FERDIGSTILT.name, kafkaValue.arsak)
        assertNull(kafkaValue.unntakArsak)
    }

    @Test
    fun `closes avvent when latest endring for person is kandidat and created before innkalling`() = runTest {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedBeforeStatusEndring)
        dialogmotekandidatVurderingService.createAvvent(
            Avvent(
                frist = LocalDate.now().plusDays(14),
                createdBy = "Z999999",
                personident = ARBEIDSTAKER_PERSONIDENTNUMBER,
                beskrivelse = "Beskrivelse"
            )
        )
        assertTrue(dialogmotekandidatVurderingService.getAvvent(ARBEIDSTAKER_PERSONIDENTNUMBER).isNotEmpty())
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        val latest = dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        assertEquals(statusEndringTidspunkt.minusDays(1).toLocalDate(), latest.createdAt.toLocalDate())
        assertTrue(latest.kandidat)
        assertTrue(dialogmotekandidatVurderingService.getAvvent(ARBEIDSTAKER_PERSONIDENTNUMBER).isEmpty())
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after ferdigstilling`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedAfterStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringOldFerdigstiltRecord,
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringFerdigstiltRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
        val latest =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latest.uuid)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person`() {
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringOldFerdigstiltRecord,
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringFerdigstiltRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
    }

    @Test
    fun `creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before lukket`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedBeforeStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringLukketRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        val slot = slot<ProducerRecord<String, DialogmotekandidatEndringRecord>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slot)) }
        val latest =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        assertFalse(latest.kandidat)
        assertEquals(DialogmotekandidatEndring.Arsak.DIALOGMOTE_LUKKET, latest.arsak)
        val kafkaValue = slot.captured.value()
        assertEquals(DialogmotekandidatEndring.Arsak.DIALOGMOTE_LUKKET.name, kafkaValue.arsak)
        assertFalse(kafkaValue.kandidat)
        assertNull(kafkaValue.unntakArsak)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after lukket`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedAfterStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringLukketRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val latest =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latest.uuid)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person (lukket)`() {
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                topicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringLukketRecord,
                )
            )
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = dialogmotekandidatRepository.getDialogmotekandidatEndringer(ARBEIDSTAKER_PERSONIDENTNUMBER)
        assertTrue(endringer.isEmpty())
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created before innkalt`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedBeforeStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertNull(ferdigstilt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after innkalt`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedAfterStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertNull(ferdigstilt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person (innkalt)`() {
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        dialogmoteStatusEndringConsumer.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt =
            database.connection.use { connection -> connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER) }
        assertNull(ferdigstilt)
    }
}
