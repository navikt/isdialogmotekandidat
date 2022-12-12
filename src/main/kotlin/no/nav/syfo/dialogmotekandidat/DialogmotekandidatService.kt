package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING
import no.nav.syfo.dialogmotestatusendring.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.*
import no.nav.syfo.oppfolgingstilfelle.domain.isDialogmotekandidat
import no.nav.syfo.unntak.domain.Unntak
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    private val database: DatabaseInterface,
) {
    fun getLatestDialogmotekandidatEndring(
        personIdent: PersonIdentNumber
    ) = database.connection.use { connection ->
        connection.getDialogmotekandidatEndringListForPerson(personIdent = personIdent)
    }.toDialogmotekandidatEndringList().firstOrNull()

    suspend fun getLatestOppfolgingstilfelle(
        personIdent: PersonIdentNumber
    ) = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(personIdent)

    suspend fun getOppfolgingstilfelleForDate(
        personIdent: PersonIdentNumber,
        date: LocalDate,
    ) = oppfolgingstilfelleService.getOppfolgingstilfelleForDate(personIdent, date)

    fun getDialogmotekandidaterWithStoppunktPlanlagtTodayOrYesterday() =
        database.getDialogmotekandidaterWithStoppunktTodayOrYesterday().toDialogmotekandidatStoppunktList()

    suspend fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val oppfolgingstilfelleOnStoppunktDate = getOppfolgingstilfelleForDate(
            personIdent = dialogmotekandidatStoppunkt.personIdent,
            date = dialogmotekandidatStoppunkt.stoppunktPlanlagt,
        )

        database.connection.use { connection ->
            val dialogmotekandidatEndringList = connection.getDialogmotekandidatEndringListForPerson(
                personIdent = dialogmotekandidatStoppunkt.personIdent
            ).toDialogmotekandidatEndringList()
            val latestDialogmoteFerdigstilt = connection.getLatestDialogmoteFerdigstiltForPerson(
                personIdent = dialogmotekandidatStoppunkt.personIdent
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

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotekandidatService::class.java)
    }
}
