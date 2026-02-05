package no.nav.syfo.application

import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.Dialogmotekandidat
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.DialogmotekandidatStoppunkt
import no.nav.syfo.domain.DialogmotekandidatStoppunktStatus
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import no.nav.syfo.domain.isDialogmotekandidat
import no.nav.syfo.domain.toDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.dialogmotekandidat.createDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.dialogmotekandidat.findOutdatedDialogmotekandidater
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidaterWithStoppunktTodayOrYesterday
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndring
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndringList
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatStoppunktList
import no.nav.syfo.infrastructure.database.dialogmotekandidat.updateDialogmotekandidatStoppunktStatus
import no.nav.syfo.infrastructure.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringProducer
import no.nav.syfo.util.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING
import no.nav.syfo.util.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    private val database: DatabaseInterface,
    private val dialogmotekandidatRepository: DialogmotekandidatRepository,
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

    fun getDialogmotekandidatEndringer(
        personident: Personident,
    ) = dialogmotekandidatRepository.getDialogmotekandidatEndringer(personident = personident)

    fun getDialogmotekandidaterWithStoppunktPlanlagtTodayOrYesterday() =
        database.getDialogmotekandidaterWithStoppunktTodayOrYesterday().toDialogmotekandidatStoppunktList()

    fun getOutdatedDialogmotekandidater(cutoff: LocalDateTime) =
        database.findOutdatedDialogmotekandidater(cutoff).toDialogmotekandidatEndringList()

    suspend fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val oppfolgingstilfelleOnStoppunktDate = oppfolgingstilfelleService.getOppfolgingstilfelleForDate(
            arbeidstakerPersonIdent = dialogmotekandidatStoppunkt.personident,
            date = dialogmotekandidatStoppunkt.stoppunktPlanlagt,
        )
        database.connection.use { connection ->
            val dialogmotekandidatEndringList = dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                personident = dialogmotekandidatStoppunkt.personident,
                connection = connection
            )

            val latestDialogmoteFerdigstilt = connection.getLatestDialogmoteFerdigstiltForPerson(
                personident = dialogmotekandidatStoppunkt.personident
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

            connection.updateDialogmotekandidatStoppunktStatus(
                uuid = dialogmotekandidatStoppunkt.uuid,
                status = status,
            )

            if (status == DialogmotekandidatStoppunktStatus.KANDIDAT) {
                val newDialogmotekandidatEndring = dialogmotekandidatStoppunkt.toDialogmotekandidatEndring()
                createDialogmotekandidatEndring(
                    connection = connection,
                    dialogmotekandidatEndring = newDialogmotekandidatEndring,
                    tilfelleStart = oppfolgingstilfelleOnStoppunktDate?.tilfelleStart,
                    unntak = null,
                )
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING.increment()
            } else {
                COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING.increment()
                log.info("Processed ${DialogmotekandidatStoppunkt::class.java.simpleName}, not kandidat - no DialogmotekandidatEndring created")
            }

            connection.commit()
        }
    }

    fun createDialogmotekandidatEndring(dialogmotekandidatEndring: DialogmotekandidatEndring) {
        database.connection.use { connection ->
            connection.createDialogmotekandidatEndring(
                dialogmotekandidatEndring = dialogmotekandidatEndring
            )
            connection.commit()
        }
        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring,
            tilfelleStart = null,
            unntak = null,
        )
    }

    fun createDialogmotekandidatEndring(
        connection: Connection,
        dialogmotekandidatEndring: DialogmotekandidatEndring,
        tilfelleStart: LocalDate?,
        unntak: Unntak?,
    ) {
        connection.createDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring
        )
        dialogmotekandidatEndringProducer.sendDialogmotekandidatEndring(
            dialogmotekandidatEndring = dialogmotekandidatEndring,
            tilfelleStart = tilfelleStart,
            unntak = unntak,
        )
    }

    fun getDialogmotekandidatEndring(uuid: UUID): DialogmotekandidatEndring? {
        return dialogmotekandidatRepository.getDialogmotekandidatEndring(uuid = uuid)?.toDialogmotekandidatEndring()
    }

    suspend fun getAvventForPersons(personidenter: List<Personident>): Map<Personident, Avvent> {
        val avventForPersons = dialogmotekandidatRepository.getAvventForPersons(personidenter = personidenter)
        return avventForPersons
            .groupBy { it.personident }
            .mapValues { it.value.maxBy { avvent -> avvent.createdAt } }
    }

    suspend fun getDialogmotekandidater(personidenter: List<Personident>): Map<Personident, Pair<DialogmotekandidatEndring, Avvent?>> {
        val latestDialogmotekandidatEndringerForPersons =
            dialogmotekandidatRepository.getDialogmotekandidatEndringForPersons(personidenter = personidenter)
                .groupBy { endring -> endring.personIdentNumber }
                .mapValues { entry -> entry.value.maxBy { it.createdAt } }
        val aktiveKandidaterIdenter = latestDialogmotekandidatEndringerForPersons.keys.toList()
        val aktiveKandidaterAvventList = getAvventForPersons(aktiveKandidaterIdenter)
        return latestDialogmotekandidatEndringerForPersons.entries.associate { entry ->
            val personident = entry.key
            val dialogmotekandidatEndring = entry.value
            val avvent = aktiveKandidaterAvventList[personident]
            val isAvventValidForLatestKandidat =
                avvent != null && dialogmotekandidatEndring.isAvventValidForDialogmotekandidatEndring(avvent)

            personident to Pair(
                first = dialogmotekandidatEndring,
                second = if (isAvventValidForLatestKandidat) avvent else null,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatService::class.java)
    }
}
