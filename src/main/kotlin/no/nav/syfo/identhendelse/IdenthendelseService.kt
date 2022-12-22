package no.nav.syfo.identhendelse

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
                log.info("Identhendelse: Updated $numberOfUpdatedIdenter personoppgaver based on Identhendelse from PDL")
                COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    private fun updateAllTables(activeIdent: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
        var numberOfUpdatedIdenter = 0
        numberOfUpdatedIdenter += updateTableWhereOldIdent(
            activeIdent = activeIdent,
            inactiveIdenter = inactiveIdenter,
            getFromDb = { database.getDialogmotekandidatStoppunktList(it) },
            updateDb = { newIdent, oldEntries -> database.updateKandidatStoppunkt(newIdent, oldEntries) }
        )
        numberOfUpdatedIdenter += updateTableWhereOldIdent(
            activeIdent = activeIdent,
            inactiveIdenter = inactiveIdenter,
            getFromDb = { database.connection.getDialogmotekandidatEndringListForPerson(it) },
            updateDb = { newIdent, oldEntries -> database.updateKandidatEndring(newIdent, oldEntries) }
        )
        numberOfUpdatedIdenter += updateTableWhereOldIdent(
            activeIdent = activeIdent,
            inactiveIdenter = inactiveIdenter,
            getFromDb = { database.getUnntakList(it) },
            updateDb = { newIdent, oldEntries -> database.updateUnntak(newIdent, oldEntries) }
        )
        numberOfUpdatedIdenter += updateTableWhereOldIdent(
            activeIdent = activeIdent,
            inactiveIdenter = inactiveIdenter,
            getFromDb = { database.getDialogmoteStatusList(it) },
            updateDb = { newIdent, oldEntries -> database.updateDialogmoteStatus(newIdent, oldEntries) }
        )

        return numberOfUpdatedIdenter
    }

    private fun <T> updateTableWhereOldIdent(
        activeIdent: PersonIdentNumber,
        inactiveIdenter: List<PersonIdentNumber>,
        getFromDb: (PersonIdentNumber) -> List<T>,
        updateDb: (PersonIdentNumber, List<T>) -> Int,
    ): Int {
        val oldIdentEntries = inactiveIdenter.flatMap { getFromDb(it) }
        return if (oldIdentEntries.isNotEmpty()) {
            checkThatPdlIsUpdated(activeIdent)
            updateDb(activeIdent, oldIdentEntries)
        } else {
            0
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdentNumber) {
        val pdlIdenter = pdlClient.getIdenter(nyIdent)?.hentIdenter
        if (nyIdent.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == nyIdent.value && it.historisk }) {
            throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }
    }
}
