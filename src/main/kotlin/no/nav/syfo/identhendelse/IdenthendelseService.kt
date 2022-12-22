package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.dialogmotekandidat.database.getDialogmotekandidatStoppunktList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.identhendelse.database.*
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import no.nav.syfo.unntak.database.getUnntakList
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val numberOfUpdatedIdenter = updateAllTables(activeIdent, inactiveIdenter)
                log.info("Identhendelse: Updated $numberOfUpdatedIdenter rows based on Identhendelse from PDL")
                COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    private fun updateAllTables(activeIdent: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
        var numberOfUpdatedIdenter = 0
        val oldStoppunktList = inactiveIdenter.flatMap { database.getDialogmotekandidatStoppunktList(it) }
        val oldEndringList = inactiveIdenter.flatMap { database.connection.getDialogmotekandidatEndringListForPerson(it) }
        val oldUnntakList = inactiveIdenter.flatMap { database.getUnntakList(it) }
        val oldStatusCount = inactiveIdenter.sumOf { database.getDialogmoteStatusCount(it) }

        if (oldStoppunktList.isNotEmpty() || oldEndringList.isNotEmpty() || oldUnntakList.isNotEmpty() || oldStatusCount > 0) {
            checkThatPdlIsUpdated(activeIdent)
            numberOfUpdatedIdenter += database.updateKandidatStoppunkt(activeIdent, oldStoppunktList)
            numberOfUpdatedIdenter += database.updateKandidatEndring(activeIdent, oldEndringList)
            numberOfUpdatedIdenter += database.updateUnntak(activeIdent, oldUnntakList)
            numberOfUpdatedIdenter += database.updateDialogmoteStatus(activeIdent, inactiveIdenter)
        }

        return numberOfUpdatedIdenter
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdentNumber) {
        runBlocking {
            val pdlIdenter = pdlClient.getPdlIdenter(nyIdent)?.hentIdenter
            if (nyIdent.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == nyIdent.value && it.historisk }) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
