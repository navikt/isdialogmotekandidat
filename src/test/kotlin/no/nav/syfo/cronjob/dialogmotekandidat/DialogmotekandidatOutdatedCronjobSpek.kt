package no.nav.syfo.cronjob.dialogmotekandidat

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.dialogmotekandidat.DialogmotekandidatService
import no.nav.syfo.dialogmotekandidat.database.DialogmotekandidatRepository
import no.nav.syfo.dialogmotekandidat.database.PDialogmotekandidatEndring
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.domain.DialogmotekandidatEndringArsak
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.kafka.KafkaDialogmotekandidatEndring
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.toOffsetDatetime
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class DialogmotekandidatOutdatedCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, KafkaDialogmotekandidatEndring>>()
        val dialogmotekandidatEndringProducer = DialogmotekandidatEndringProducer(
            kafkaProducerDialogmotekandidatEndring = kafkaProducer,
        )
        val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
        val cutoff = LocalDate.now()
        val dialogmotekandidatService = DialogmotekandidatService(
            oppfolgingstilfelleService = mockk(),
            dialogmotekandidatEndringProducer = dialogmotekandidatEndringProducer,
            database = database,
            dialogmotekandidatRepository = DialogmotekandidatRepository(database),
        )
        val dialogmotekandidatOutdatedCronjob = DialogmotekandidatOutdatedCronjob(
            outdatedDialogmotekandidatCutoff = cutoff,
            dialogmotekandidatService = dialogmotekandidatService,
        )

        fun getDialogmotekandidatEndringer(personIdent: PersonIdentNumber): List<PDialogmotekandidatEndring> =
            database.connection.use { connection -> connection.getDialogmotekandidatEndringListForPerson(personIdent) }

        beforeEachTest {
            database.dropData()

            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        describe("${DialogmotekandidatOutdatedCronjob::class.java.simpleName}: run job") {
            it("creates DialogmotekandidatEndring (LUKKET kandidat=false) for person kandidat before cutoff-date with no other DialogmotekandidatEndring") {
                val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(kandidatBeforeCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, KafkaDialogmotekandidatEndring>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }
                val kafkaDialogmoteKandidatEndring = producerRecordSlot.captured.value()
                kafkaDialogmoteKandidatEndring.kandidat shouldBeEqualTo false

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 2
                val latestEndring = dialogmoteKandidatEndringer.first()
                latestEndring.arsak shouldBeEqualTo DialogmotekandidatEndringArsak.LUKKET.name
                latestEndring.kandidat shouldBeEqualTo false
            }
            it("updates persons kandidat before cutoff-date only once") {
                val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime()
                )
                val otherKandidatBeforeCutoff =
                    generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT).copy(
                        createdAt = cutoff.minusDays(100).toOffsetDatetime()
                    )
                val notKandidatBeforeCutoff =
                    generateDialogmotekandidatEndringFerdigstilt(UserConstants.ARBEIDSTAKER_2_PERSONIDENTNUMBER).copy(
                        createdAt = cutoff.minusDays(50).toOffsetDatetime()
                    )
                val kandidatAfterCutoff =
                    generateDialogmotekandidatEndringStoppunkt(UserConstants.ARBEIDSTAKER_3_PERSONIDENTNUMBER).copy(
                        createdAt = cutoff.plusDays(1).toOffsetDatetime()
                    )
                listOf(
                    kandidatBeforeCutoff,
                    otherKandidatBeforeCutoff,
                    notKandidatBeforeCutoff,
                    kandidatAfterCutoff
                ).forEach {
                    database.createDialogmotekandidatEndring(it)
                }

                var result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 2

                result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }
            it("creates no DialogmotekandidatEndring for person with no DialogmotekandidatEndring") {
                dialogmotekandidatOutdatedCronjob.runJob()

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 0
            }
            it("creates no DialogmotekandidatEndring for person not kandidat before cutoff-date") {
                val notKandidatBeforeCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(notKandidatBeforeCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 1
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
            it("creates no DialogmotekandidatEndring for person not kandidat after cutoff-date") {
                val notKandidatAfterCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
                    createdAt = cutoff.plusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(notKandidatAfterCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 1
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
            it("creates no DialogmotekandidatEndring for person kandidat after cutoff-date") {
                val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.plusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(kandidatAfterCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 1
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
            it("creates no DialogmotekandidatEndring for person kandidat before cutoff-date but not kandidat after cutoff-date") {
                val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime()
                )
                val notKandidatAfterCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
                    createdAt = cutoff.plusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(kandidatBeforeCutoff)
                database.createDialogmotekandidatEndring(notKandidatAfterCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 2
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
            it("creates no DialogmotekandidatEndring for person not kandidat before cutoff-date but kandidat after cutoff-date") {
                val notKandidatBeforeCutoff = generateDialogmotekandidatEndringFerdigstilt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime(),
                )
                val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.plusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(notKandidatBeforeCutoff)
                database.createDialogmotekandidatEndring(kandidatAfterCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 2
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
            it("creates no DialogmotekandidatEndring for person kandidat before cutoff-date but also kandidat after cutoff-date") {
                val kandidatBeforeCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.minusDays(1).toOffsetDatetime()
                )
                val kandidatAfterCutoff = generateDialogmotekandidatEndringStoppunkt(personIdent).copy(
                    createdAt = cutoff.plusDays(1).toOffsetDatetime()
                )
                database.createDialogmotekandidatEndring(kandidatBeforeCutoff)
                database.createDialogmotekandidatEndring(kandidatAfterCutoff)

                val result = dialogmotekandidatOutdatedCronjob.runJob()
                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val dialogmoteKandidatEndringer = getDialogmotekandidatEndringer(personIdent)
                dialogmoteKandidatEndringer.size shouldBeEqualTo 2
                dialogmoteKandidatEndringer.none { it.arsak == DialogmotekandidatEndringArsak.LUKKET.name }
            }
        }
    }
})
