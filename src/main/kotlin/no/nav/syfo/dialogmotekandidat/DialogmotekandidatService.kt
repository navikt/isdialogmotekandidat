package no.nav.syfo.dialogmotekandidat

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.dialogmotekandidat.database.*
import no.nav.syfo.dialogmotekandidat.domain.*
import no.nav.syfo.dialogmotekandidat.kafka.DialogmotekandidatEndringProducer
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_CREATED_KANDIDATENDRING
import no.nav.syfo.dialogmotekandidat.metric.COUNT_DIALOGMOTEKANDIDAT_STOPPUNKT_SKIPPED_NOT_KANDIDATENDRING
import no.nav.syfo.dialogmotestatusendring.database.getLatestDialogmoteFerdigstiltForPerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.*
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.util.isAfterOrEqual
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime

class DialogmotekandidatService(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatEndringProducer: DialogmotekandidatEndringProducer,
    private val database: DatabaseInterface,
) {
    fun getLatestDialogmotekandidatEndring(personIdent: PersonIdentNumber): DialogmotekandidatEndring? {
        return database.connection.use { connection ->
            connection.getDialogmotekandidatEndringListForPerson(personIdent = personIdent)
        }.toDialogmotekandidatEndringList().firstOrNull()
    }

    suspend fun getLatestOppfolgingstilfelle(personIdentNumber: PersonIdentNumber): OppfolgingstilfelleDTO? =
        oppfolgingstilfelleService.getLatestOppfolgingstilfelle(personIdentNumber)

    suspend fun getOppfolgingstilfelleForDate(
        personIdentNumber: PersonIdentNumber,
        date: LocalDate,
    ): OppfolgingstilfelleDTO? =
        oppfolgingstilfelleService.getOppfolgingstilfelleForDate(personIdentNumber, date)

    fun getDialogmotekandidaterWithStoppunktPlanlagtTodayOrYesterday(): List<DialogmotekandidatStoppunkt> =
        database.getDialogmotekandidaterWithStoppunktTodayOrYesterday().toDialogmotekandidatStoppunktList()

    suspend fun updateDialogmotekandidatStoppunktStatus(
        dialogmotekandidatStoppunkt: DialogmotekandidatStoppunkt,
    ) {
        val oppfolgingstilfelleForDate = getOppfolgingstilfelleForDate(
            personIdentNumber = dialogmotekandidatStoppunkt.personIdent,
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
                oppfolgingstilfelleForDate != null &&
                isDialogmotekandidat(
                    oppfolgingstilfelle = oppfolgingstilfelleForDate,
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
                    tilfelleStart = oppfolgingstilfelleForDate?.start,
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

    private fun isDialogmotekandidat(
        oppfolgingstilfelle: OppfolgingstilfelleDTO,
        dialogmotekandidatEndringList: List<DialogmotekandidatEndring>,
        latestDialogmoteFerdigstilt: OffsetDateTime?,
    ) = isDialogmotekandidatTilfelle(oppfolgingstilfelle) &&
        (latestDialogmoteFerdigstilt == null || latestDialogmoteFerdigstilt.toLocalDate().isBefore(oppfolgingstilfelle.start)) &&
        dialogmotekandidatEndringList.isLatestStoppunktKandidatMissingOrNotInOppfolgingstilfelle(oppfolgingstilfelle.start)

    fun isDialogmotekandidatTilfelle(
        oppfolgingstilfelle: OppfolgingstilfelleDTO
    ): Boolean {
        val tilfelleStart = oppfolgingstilfelle.start
        val tilfelleEnd = oppfolgingstilfelle.end
        val stoppunkt = DialogmotekandidatStoppunkt.stoppunktPlanlagtDato(tilfelleStart, tilfelleEnd)
        return tilfelleEnd.isAfterOrEqual(stoppunkt)
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
