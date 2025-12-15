package no.nav.syfo.application

import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.getDialogmotekandidatEndringListForPerson
import no.nav.syfo.infrastructure.database.dialogmotekandidat.toDialogmotekandidatEndringList
import no.nav.syfo.infrastructure.database.toAvventList
import no.nav.syfo.infrastructure.database.toIkkeAktuellList
import no.nav.syfo.infrastructure.database.toUnntakList

class DialogmotekandidatVurderingService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val dialogmotekandidatVurderingRepository: IDialogmotekandidatVurderingRepository,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    suspend fun createIkkeAktuell(
        ikkeAktuell: IkkeAktuell,
        veilederToken: String,
        callId: String,
    ) {
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personident = ikkeAktuell.personident)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create IkkeAktuell: Person is not kandidat")
            }

            dialogmotekandidatVurderingRepository.createIkkeAktuell(connection = connection, commit = false, ikkeAktuell = ikkeAktuell)
            val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
                arbeidstakerPersonIdent = ikkeAktuell.personident,
                veilederToken = veilederToken,
                callId = callId,
            )
            val newDialogmotekandidatEndring = DialogmotekandidatEndring.ikkeAktuell(
                personIdentNumber = ikkeAktuell.personident,
            )
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
                unntak = null,
            )
            connection.commit()
        }
    }

    suspend fun getIkkeAktuellList(personident: Personident): List<IkkeAktuell> =
        dialogmotekandidatVurderingRepository.getIkkeAktuellListForPerson(personident = personident).toIkkeAktuellList()

    suspend fun createUnntak(
        unntak: Unntak,
        veilederToken: String,
        callId: String,
    ) {
        val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
            arbeidstakerPersonIdent = unntak.personident,
            veilederToken = veilederToken,
            callId = callId,
        )
        database.connection.use { connection ->
            val ikkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personident = unntak.personident)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }
            dialogmotekandidatVurderingRepository.createUnntak(connection = connection, unntak = unntak)
            val newDialogmotekandidatEndring = DialogmotekandidatEndring.unntak(
                personIdentNumber = unntak.personident,
            )
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = newDialogmotekandidatEndring,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
                unntak = unntak,
            )
            connection.commit()
        }
    }

    suspend fun getUnntakList(personident: Personident): List<Unntak> =
        dialogmotekandidatVurderingRepository.getUnntakList(personident = personident).toUnntakList()

    suspend fun createAvvent(
        avvent: Avvent,
    ) {
        database.connection.use { connection ->
            val isPersonIkkeKandidat =
                connection.getDialogmotekandidatEndringListForPerson(personident = avvent.personident)
                    .toDialogmotekandidatEndringList()
                    .isLatestIkkeKandidat()
            if (isPersonIkkeKandidat) {
                throw ConflictException("Failed to create Avvent: Person is not kandidat")
            }
            dialogmotekandidatVurderingRepository.createAvvent(connection = connection, avvent = avvent)
            connection.commit()
        }
    }

    suspend fun getAvventList(personident: Personident): List<Avvent> {
        val allAvvent = dialogmotekandidatVurderingRepository.getAvventList(personident = personident).toAvventList()
        val latestEndring = dialogmotekandidatService.getDialogmotekandidatEndringer(personident).latest()
        return if (latestEndring?.kandidat == true) {
            allAvvent.filter { it.createdAt.isAfter(latestEndring.createdAt) }
        } else emptyList()
    }
}
