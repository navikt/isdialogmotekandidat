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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Future

class KafkaDialogmoteStatusEndringServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    private val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )
    private val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    private val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )

    private val kafkaDialogmoteStatusEndringService = KafkaDialogmoteStatusEndringService(
        database = database,
        dialogmotekandidatService = dialogmotekandidatService,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )

    private val partition = 0
    private val dialogmoteStatusEndringTopicPartition = TopicPartition(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition
    )

    private val moteTidspunkt = OffsetDateTime.now().minusDays(1)
    private val statusEndringTidspunkt = OffsetDateTime.now()
    private val dialogmotekandidatEndringCreatedBeforeStatusEndring = generateDialogmotekandidatEndringStoppunkt(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    ).copy(
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
    private val dialogmotekandidatEndringCreatedAfterStatusEndring = generateDialogmotekandidatEndringStoppunkt(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    ).copy(
        createdAt = statusEndringTidspunkt.plusDays(1)
    )

    private val kDialogmoteStatusEndringOldFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        1,
        "key1",
        kDialogmoteStatusEndringOldFerdigstilt
    )
    private val kDialogmoteStatusEndringInnkaltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        2,
        "key2",
        kDialogmoteStatusEndringInnkalt
    )
    private val kDialogmoteStatusEndringFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        3,
        "key3",
        kDialogmoteStatusEndringFerdigstilt
    )
    private val kDialogmoteStatusEndringLukketRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        4,
        "key4",
        kDialogmoteStatusEndringLukket
    )
    private val mockKafkaConsumerDialogmoteStatusEndring = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()

    @BeforeEach
    fun setup() {
        database.dropData()
        clearMocks(kafkaProducer, mockKafkaConsumerDialogmoteStatusEndring)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
        every { mockKafkaConsumerDialogmoteStatusEndring.commitSync() } returns Unit
    }

    private fun setupFerdigstiltConsumer() {
        every { mockKafkaConsumerDialogmoteStatusEndring.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                dialogmoteStatusEndringTopicPartition to listOf(
                    kDialogmoteStatusEndringOldFerdigstiltRecord,
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringFerdigstiltRecord,
                )
            )
        )
    }

    @Test
    fun `creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before ferdigstilling`() {
        setupFerdigstiltConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedBeforeStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertEquals(statusEndringTidspunkt.toLocalDate(), latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate())

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
            ).firstOrNull()

        assertEquals(false, latestDialogmotekandidatEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, latestDialogmotekandidatEndring.arsak)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(false, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after ferdigstilling`() {
        setupFerdigstiltConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedAfterStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertEquals(statusEndringTidspunkt.toLocalDate(), latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate())

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
            ).firstOrNull()

        assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latestDialogmotekandidatEndring!!.uuid)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person`() {
        setupFerdigstiltConsumer()
        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }
        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertEquals(statusEndringTidspunkt.toLocalDate(), latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate())
    }

    private fun setupLukketConsumer() {
        every { mockKafkaConsumerDialogmoteStatusEndring.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                dialogmoteStatusEndringTopicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord,
                    kDialogmoteStatusEndringLukketRecord,
                )
            )
        )
    }

    @Test
    fun `creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before lukket`() {
        setupLukketConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedBeforeStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
            ).firstOrNull()

        assertEquals(false, latestDialogmotekandidatEndring!!.kandidat)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name, latestDialogmotekandidatEndring.arsak)

        val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
        assertEquals(ARBEIDSTAKER_PERSONIDENTNUMBER.value, kafkaDialogmoteKandidatEndring.personIdentNumber)
        assertEquals(DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name, kafkaDialogmoteKandidatEndring.arsak)
        assertEquals(false, kafkaDialogmoteKandidatEndring.kandidat)
        assertNull(kafkaDialogmoteKandidatEndring.unntakArsak)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after lukket`() {
        setupLukketConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedAfterStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val latestDialogmotekandidatEndring =
            database.connection.getDialogmotekandidatEndringListForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
            ).firstOrNull()

        assertEquals(dialogmotekandidatEndringCreatedAfterStatusEndring.uuid, latestDialogmotekandidatEndring!!.uuid)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person - lukket`() {
        setupLukketConsumer()
        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val endringList = database.connection.getDialogmotekandidatEndringListForPerson(personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER)
        assertTrue(endringList.isEmpty())
    }

    private fun setupInnkaltConsumer() {
        every { mockKafkaConsumerDialogmoteStatusEndring.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                dialogmoteStatusEndringTopicPartition to listOf(
                    kDialogmoteStatusEndringInnkaltRecord
                )
            )
        )
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created before innkalt`() {
        setupInnkaltConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedBeforeStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }
        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertNull(latestDialogmoteFerdigstiltTidspunkt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after innkalt`() {
        setupInnkaltConsumer()
        database.createDialogmotekandidatEndring(dialogmotekandidatEndring = dialogmotekandidatEndringCreatedAfterStatusEndring)

        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }
        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertNull(latestDialogmoteFerdigstiltTidspunkt)
    }

    @Test
    fun `creates no new DialogmotekandidatEndring when no latest endring for person - innkalt`() {
        setupInnkaltConsumer()
        kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
            kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
        )

        verify(exactly = 1) {
            mockKafkaConsumerDialogmoteStatusEndring.commitSync()
        }
        verify(exactly = 0) {
            kafkaProducer.send(any())
        }
        val latestDialogmoteFerdigstiltTidspunkt =
            database.connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
            )
        assertNull(latestDialogmoteFerdigstiltTidspunkt)
    }
}
