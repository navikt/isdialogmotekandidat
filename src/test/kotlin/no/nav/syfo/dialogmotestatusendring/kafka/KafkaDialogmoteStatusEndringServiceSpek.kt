package no.nav.syfo.dialogmotestatusendring.kafka

import io.mockk.*
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.application.DialogmotekandidatService
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DIALOGMOTE_STATUS_ENDRING_TOPIC
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.KafkaDialogmoteStatusEndringService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateKDialogmoteStatusEndring
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.util.concurrent.Future

class KafkaDialogmoteStatusEndringServiceSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
    val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
        kafkaProducerDialogmotekandidatEndring = kafkaProducer,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    val dialogmotekandidatService = DialogmotekandidatService(
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
        database = database,
        dialogmotekandidatRepository = DialogmotekandidatRepository(database),
    )

    val kafkaDialogmoteStatusEndringService = KafkaDialogmoteStatusEndringService(
        database = database,
        dialogmotekandidatService = dialogmotekandidatService,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
    )

    val partition = 0
    val dialogmoteStatusEndringTopicPartition = TopicPartition(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition
    )

    val moteTidspunkt = OffsetDateTime.now().minusDays(1)
    val statusEndringTidspunkt = OffsetDateTime.now()
    val dialogmotekandidatEndringCreatedBeforeStatusEndring = generateDialogmotekandidatEndringStoppunkt(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    ).copy(
        createdAt = statusEndringTidspunkt.minusDays(1)
    )
    val kDialogmoteStatusEndringOldFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt.minusYears(1),
        endringsTidspunkt = statusEndringTidspunkt.minusYears(1),
    )
    val kDialogmoteStatusEndringInnkalt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.INNKALT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = moteTidspunkt,
    )
    val kDialogmoteStatusEndringFerdigstilt = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.FERDIGSTILT,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    val kDialogmoteStatusEndringLukket = generateKDialogmoteStatusEndring(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        statusEndringType = DialogmoteStatusEndringType.LUKKET,
        moteTidspunkt = moteTidspunkt,
        endringsTidspunkt = statusEndringTidspunkt,
    )
    val dialogmotekandidatEndringCreatedAfterStatusEndring = generateDialogmotekandidatEndringStoppunkt(
        personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    ).copy(
        createdAt = statusEndringTidspunkt.plusDays(1)
    )

    val kDialogmoteStatusEndringOldFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        1,
        "key1",
        kDialogmoteStatusEndringOldFerdigstilt
    )
    val kDialogmoteStatusEndringInnkaltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        2,
        "key2",
        kDialogmoteStatusEndringInnkalt
    )
    val kDialogmoteStatusEndringFerdigstiltRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        3,
        "key3",
        kDialogmoteStatusEndringFerdigstilt
    )
    val kDialogmoteStatusEndringLukketRecord = ConsumerRecord(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        partition,
        4,
        "key4",
        kDialogmoteStatusEndringLukket
    )
    val mockKafkaConsumerDialogmoteStatusEndring = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()

    beforeEachTest {
        database.dropData()
        clearMocks(kafkaProducer, mockKafkaConsumerDialogmoteStatusEndring)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
        every { mockKafkaConsumerDialogmoteStatusEndring.commitSync() } returns Unit
    }

    describe("${KafkaDialogmoteStatusEndringService::class.java.simpleName}: pollAndProcessRecords") {
        describe("receive DialogmoteStatusEndring Ferdigstilt") {
            beforeEachTest {
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

            it("creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before ferdigstilling") {
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
                latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate() shouldBeEqualTo statusEndringTidspunkt.toLocalDate()

                val latestDialogmotekandidatEndring =
                    database.connection.getDialogmotekandidatEndringListForPerson(
                        personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
                    ).firstOrNull()

                latestDialogmotekandidatEndring!!.kandidat shouldBeEqualTo false
                latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name

                val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
                kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo ARBEIDSTAKER_PERSONIDENTNUMBER.value
                kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name
                kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo false
                kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            }
            it("creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after ferdigstilling") {
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
                latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate() shouldBeEqualTo statusEndringTidspunkt.toLocalDate()

                val latestDialogmotekandidatEndring =
                    database.connection.getDialogmotekandidatEndringListForPerson(
                        personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
                    ).firstOrNull()

                latestDialogmotekandidatEndring!!.uuid shouldBeEqualTo dialogmotekandidatEndringCreatedAfterStatusEndring.uuid
            }
            it("creates no new DialogmotekandidatEndring when no latest endring for person") {
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
                latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate() shouldBeEqualTo statusEndringTidspunkt.toLocalDate()
            }
        }
        describe("receive DialogmoteStatusEndring Lukket") {
            beforeEachTest {
                every { mockKafkaConsumerDialogmoteStatusEndring.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        dialogmoteStatusEndringTopicPartition to listOf(
                            kDialogmoteStatusEndringInnkaltRecord,
                            kDialogmoteStatusEndringLukketRecord,
                        )
                    )
                )
            }

            it("creates new DialogmotekandidatEndring(not kandidat) when latest endring for person is kandidat and created before lukket") {
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

                latestDialogmotekandidatEndring!!.kandidat shouldBeEqualTo false
                latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name

                val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
                kafkaDialogmoteKandidatEndring.personIdentNumber shouldBeEqualTo ARBEIDSTAKER_PERSONIDENTNUMBER.value
                kafkaDialogmoteKandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_LUKKET.name
                kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo false
                kafkaDialogmoteKandidatEndring.unntakArsak shouldBeEqualTo null
            }
            it("creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after lukket") {
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

                latestDialogmotekandidatEndring!!.uuid shouldBeEqualTo dialogmotekandidatEndringCreatedAfterStatusEndring.uuid
            }
            it("creates no new DialogmotekandidatEndring when no latest endring for person") {
                kafkaDialogmoteStatusEndringService.pollAndProcessRecords(
                    kafkaConsumerDialogmoteStatusEndring = mockKafkaConsumerDialogmoteStatusEndring
                )

                verify(exactly = 1) {
                    mockKafkaConsumerDialogmoteStatusEndring.commitSync()
                }
                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                database.connection.getDialogmotekandidatEndringListForPerson(personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER).shouldBeEmpty()
            }
        }
        describe("receive DialogmoteStatusEndring not Ferdigstilt (Innkalt)") {
            beforeEachTest {
                every { mockKafkaConsumerDialogmoteStatusEndring.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        dialogmoteStatusEndringTopicPartition to listOf(
                            kDialogmoteStatusEndringInnkaltRecord
                        )
                    )
                )
            }

            it("creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created before innkalt") {
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
                latestDialogmoteFerdigstiltTidspunkt shouldBe null
            }
            it("creates no new DialogmotekandidatEndring when latest endring for person is kandidat and created after innkalt") {
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
                latestDialogmoteFerdigstiltTidspunkt shouldBe null
            }
            it("creates no new DialogmotekandidatEndring when no latest endring for person") {
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
                latestDialogmoteFerdigstiltTidspunkt shouldBe null
            }
        }
    }
})
