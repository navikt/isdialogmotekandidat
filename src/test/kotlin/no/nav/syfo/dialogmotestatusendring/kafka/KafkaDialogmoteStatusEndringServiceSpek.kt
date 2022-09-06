package no.nav.syfo.dialogmotestatusendring.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotestatusendring.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusEndringType
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateKDialogmoteStatusEndring
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*

class KafkaDialogmoteStatusEndringServiceSpek : Spek({

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val dialogmotekandidatEndringProducer = mockk<DialogmotekandidatEndringProducer>()
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database
        )
        val dialogmotekandidatService = DialogmotekandidatService(
            oppfolgingstilfelleService = oppfolgingstilfelleService,
            dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            database = database,
        )

        val kafkaDialogmoteStatusEndringService = KafkaDialogmoteStatusEndringService(
            database = database,
            dialogmotekandidatService = dialogmotekandidatService,
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
        val mockKafkaConsumerDialogmoteStatusEndring = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()

        beforeEachTest {
            database.dropData()

            clearMocks(dialogmotekandidatEndringProducer, mockKafkaConsumerDialogmoteStatusEndring)
            justRun { dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any()) }
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
                    verify(exactly = 1) {
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }

                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
                        personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
                    )
                    latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate() shouldBeEqualTo statusEndringTidspunkt.toLocalDate()

                    val latestDialogmotekandidatEndring =
                        database.connection.getDialogmotekandidatEndringListForPerson(
                            personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER
                        ).firstOrNull()

                    latestDialogmotekandidatEndring!!.kandidat shouldBeEqualTo false
                    latestDialogmotekandidatEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.DIALOGMOTE_FERDIGSTILT.name
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
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }

                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
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
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }
                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
                        personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
                    )
                    latestDialogmoteFerdigstiltTidspunkt!!.toLocalDate() shouldBeEqualTo statusEndringTidspunkt.toLocalDate()
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
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }
                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
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
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }
                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
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
                        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(any(), any(), any())
                    }
                    val latestDialogmoteFerdigstiltTidspunkt = database.connection.getLatestDialogmoteFerdigstiltForPerson(
                        personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER,
                    )
                    latestDialogmoteFerdigstiltTidspunkt shouldBe null
                }
            }
        }
    }
})
