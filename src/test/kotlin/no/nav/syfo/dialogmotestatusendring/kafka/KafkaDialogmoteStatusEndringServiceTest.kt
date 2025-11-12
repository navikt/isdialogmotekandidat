package no.nav.syfo.dialogmotestatusendring.kafka

import io.mockk.*
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DIALOGMOTE_STATUS_ENDRING_TOPIC
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.KafkaDialogmoteStatusEndringService
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
import org.junit.jupiter.api.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Future

class KafkaDialogmoteStatusEndringServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(kafkaProducerDialogmotekandidatEndring = kafkaProducer)
    private val azureAdClient = AzureAdClient(externalMockEnvironment.environment.azure, externalMockEnvironment.mockHttpClient)
    private val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfelleClient)
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )
    private val service = KafkaDialogmoteStatusEndringService(
        database = database,
        dialogmotekandidatService = dialogmotekandidatService,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )

    private val partition = 0
    private val topicPartition = TopicPartition(DIALOGMOTE_STATUS_ENDRING_TOPIC, partition)
    private val consumer = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()

    private val moteTidspunkt = OffsetDateTime.now().minusDays(1)
    private val statusEndringTidspunkt = OffsetDateTime.now()
    private val dialogmotekandidatEndringCreatedBeforeStatusEndring = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER).copy(
        createdAt = statusEndringTidspunkt.minusDays(1)
    )
    private val kDialogmoteStatusEndringOldFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt.minusYears(1),
        endringsTidspunkt = statusEndringTidspunkt.minusYears(1),
    )
    private val kDialogmoteStatusEndringInnkalt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.INNKALT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = moteTidspunkt,
    )
    private val kDialogmoteStatusEndringFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    private val kDialogmoteStatusEndringLukket = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.LUKKET,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    private val dialogmotekandidatEndringCreatedAfterStatusEndring = generateDialogmotekandidatEndringStoppunkt(ARBEIDSTAKER_PERSONIDENTNUMBER).copy(
        createdAt = statusEndringTidspunkt.plusDays(1)
    )

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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        val slot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slot)) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
        val latest = database.connection.getDialogmotekandidatEndringListForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        Assertions.assertFalse(latest.kandidat)
        Assertions.assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, latest.arsak)
        val kafkaValue = slot.captured.value()
        Assertions.assertEquals(ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaValue.personIdentNumber)
        Assertions.assertFalse(kafkaValue.kandidat)
        Assertions.assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, kafkaValue.arsak)
        Assertions.assertNull(kafkaValue.unntakArsak)
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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
        val latest = database.connection.getDialogmotekandidatEndringListForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        Assertions.assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latest.uuid)
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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertEquals(statusEndringTidspunkt.toLocalDate(), ferdigstilt!!.toLocalDate())
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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        val slot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slot)) }
        val latest = database.connection.getDialogmotekandidatEndringListForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        Assertions.assertFalse(latest.kandidat)
        Assertions.assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name, latest.arsak)
        val kafkaValue = slot.captured.value()
        Assertions.assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name, kafkaValue.arsak)
        Assertions.assertFalse(kafkaValue.kandidat)
        Assertions.assertNull(kafkaValue.unntakArsak)
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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val latest = database.connection.getDialogmotekandidatEndringListForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER).first()
        Assertions.assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latest.uuid)
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
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val endringer = database.connection.getDialogmotekandidatEndringListForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertTrue(endringer.isEmpty())
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created before innkalt`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedBeforeStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertNull(ferdigstilt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after innkalt`() {
        database.createDialogmotekandidatEndring(dialogmotekandidatEndringCreatedAfterStatusEndring)
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertNull(ferdigstilt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person (innkalt)`() {
        every { consumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to listOf(kDialogmoteStatusEndringInnkaltRecord))
        )
        service.pollAndProcessRecords(consumer)
        verify(exactly = 1) { consumer.commitSync() }
        verify(exactly = 0) { kafkaProducer.send(any()) }
        val ferdigstilt = database.connection.getLatestDialogmoteFerdigstiltForPerson(ARBEIDSTAKER_PERSONIDENTNUMBER)
        Assertions.assertNull(ferdigstilt)
    }
}
