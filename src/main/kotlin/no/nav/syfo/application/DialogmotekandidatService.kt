package no.nav.syfo.application

import no.nav.syfo.domain.Dialogmotekandidat
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DialogmoteStatusRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatStoppunktRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.util.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING
import no.nav.syfo.util.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    private val transactionManager: ITransactionManager,
    private val dialogmotekandidatRepository: DialogmotekandidatRepository,
    private val dialogmotekandidatStoppunktRepository: DialogmotekandidatStoppunktRepository,
    private val dialogmoteStatusRepository: DialogmoteStatusRepository,
) {

    suspend fun getKandidat(
        personident: Personident,
        veilederToken: String?,
        callId: String,
    ): Dialogmotekandidat {
        val oppfolgingstilfelle = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            veilederToken = veilederToken,
            callId = callId,
        )
        val latestKandidatEndring: DialogmotekandidatEndring? =
            getDialogmotekandidatEndringer(personident = personident).firstOrNull()
        return Dialogmotekandidat.create(
            latestDialogmotekandidatEndring = latestKandidatEndring,
            latestOppfolgingstilfelleStart = oppfolgingstilfelle?.tilfelleStart
        )
    }

    suspend fun getDialogmotekandidatEndringer(
        personident: Personident,
    ) = transactionManager.run { transaction ->
        dialogmotekandidatRepository.getDialogmotekandidatEndringer(transaction, personident)
    }

    fun getDialogmotekandidaterWithStoppunktPlanlagtTodayOrYesterday() =
        dialogmotekandidatStoppunktRepository.getDialogmotekandidaterWithStoppunktTodayOrYesterday().toDialogmotekandidatStoppunktList()

    fun getOutdatedDialogmotekandidater(cutoff: LocalDateTime) =
        dialogmotekandidatRepository.findOutdatedDialogmotekandidater(cutoff)

    suspend fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val oppfolgingstilfelleOnStoppunktDate = oppfolgingstilfelleService.getOppfolgingstilfelleForDate(
            arbeidstakerPersonIdent = dialogmotekandidatStoppunkt.personident,
            date = dialogmotekandidatStoppunkt.stoppunktPlanlagt,
        )
        transactionManager.run { transaction ->
            val dialogmotekandidatEndringList = dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                personident = dialogmotekandidatStoppunkt.personident,
                transaction = transaction,
            )

            val latestDialogmoteFerdigstilt = dialogmoteStatusRepository.getLatestDialogmoteFerdigstiltForPerson(
                transaction = transaction,
                personident = dialogmotekandidatStoppunkt.personident,
            )
            val status = if (
                oppfolgingstilfelleOnStoppunktDate != null &&
                oppfolgingstilfelleOnStoppunktDate.isDialogmotekandidat(
                    dialogmotekandidatEndringList = dialogmotekandidatEndringList,
                    latestDialogmoteFerdigstilt = latestDialogmoteFerdigstilt,
                )
            )
                DialogmotekandidatStoppunktStatus.KANDIDAT
            else
                DialogmotekandidatStoppunktStatus.IKKE_KANDIDAT

            dialogmotekandidatStoppunktRepository.updateDialogmotekandidatStoppunktStatus(
                transaction = transaction,
                uuid = dialogmotekandidatStoppunkt.uuid,
                status = status,
            )

            if (status == DialogmotekandidatStoppunktStatus.KANDIDAT) {
                val dialogmoteKandidat =
                    DialogmotekandidatEndring.createKandidat(personident = dialogmotekandidatStoppunkt.personident)
                createDialogmotekandidatEndring(
                    transaction = transaction,
                    dialogmotekandidatEndring = dialogmoteKandidat,
                    tilfelleStart = oppfolgingstilfelleOnStoppunktDate?.tilfelleStart,
                )
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING.increment()
            } else {
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING.increment()
                log.info("Processed ${DialogmotekandidatStoppunkt::class.java.simpleName}, not kandidat - no DialogmotekandidatEndring created")
            }
        }
    }

    suspend fun createDialogmotekandidatEndring(dialogmotekandidatEndring: DialogmotekandidatEndring) {
        transactionManager.run { transaction ->
            createDialogmotekandidatEndring(
                transaction = transaction,
                dialogmotekandidatEndring = dialogmotekandidatEndring,
                tilfelleStart = null,
            )
        }
    }

    fun createDialogmotekandidatEndring(
        transaction: ITransaction,
        dialogmotekandidatEndring: DialogmotekandidatEndring,
        tilfelleStart: LocalDate?,
    ) {
        dialogmotekandidatRepository.createDialogmotekandidatEndring(
            transaction = transaction,
            dialogmotekandidatEndring = dialogmotekandidatEndring,
        )
        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring,
            tilfelleStart = tilfelleStart,
        )
    }

    fun getDialogmotekandidatEndring(uuid: UUID): DialogmotekandidatEndring? {
        return dialogmotekandidatRepository.getDialogmotekandidatEndring(uuid = uuid)?.toDialogmotekandidatEndring()
    }

    fun getAvventForPersons(personidenter: List<Personident>): Map<Personident, DialogmotekandidatEndring.Avvent> {
        val avventForPersons = dialogmotekandidatRepository.getAvventForPersons(personidenter = personidenter)
        return avventForPersons
            .groupBy { it.personident }
            .mapValues { it.value.maxBy { avvent -> avvent.createdAt } }
    }

    fun getDialogmotekandidater(personidenter: List<Personident>): Map<Personident, DialogmotekandidatEndring> {
        val latestDialogmotekandidatEndringerForPersons =
            dialogmotekandidatRepository.getDialogmotekandidatEndringForPersons(personidenter = personidenter)
                .groupBy { endring -> endring.personident }
                .mapValues { entry -> entry.value.maxBy { it.createdAt } }
        val aktiveKandidaterIdenter = latestDialogmotekandidatEndringerForPersons.keys.toList()
        val aktiveKandidaterAvventList = getAvventForPersons(aktiveKandidaterIdenter)
        return latestDialogmotekandidatEndringerForPersons.entries.associate { entry ->
            val personident = entry.key
            val dialogmotekandidatEndring = entry.value
            val avvent = aktiveKandidaterAvventList[personident]
            val isAvventValidForLatestKandidat =
                avvent != null &&
                    dialogmotekandidatEndring.kandidat &&
                    avvent.createdAt.isAfter(dialogmotekandidatEndring.createdAt) &&
                    !avvent.isLukket

            personident to if (isAvventValidForLatestKandidat) avvent else dialogmotekandidatEndring
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatService::class.java)
    }
}
