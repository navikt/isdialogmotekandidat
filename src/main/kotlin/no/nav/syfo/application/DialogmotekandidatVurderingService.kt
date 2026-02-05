package no.nav.syfo.application

import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import no.nav.syfo.domain.isLatestIkkeKandidat
import no.nav.syfo.domain.latest
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import no.nav.syfo.infrastructure.database.toAvventList
import no.nav.syfo.infrastructure.database.toIkkeAktuellList
import no.nav.syfo.infrastructure.database.toUnntakList
import java.sql.Connection

class DialogmotekandidatVurderingService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatRepository: DialogmotekandidatRepository,
    private val dialogmotekandidatVurderingRepository: IDialogmotekandidatVurderingRepository,
) {
    suspend fun createIkkeAktuell(
        ikkeAktuell: IkkeAktuell,
        veilederToken: String,
        callId: String,
    ) {
        val ikkeKandidat =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(personident = ikkeAktuell.personident)
                .isLatestIkkeKandidat()
        if (ikkeKandidat) {
            throw ConflictException("Failed to create IkkeAktuell: Person is not kandidat")
        }

        database.connection.use { connection ->
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
        val ikkeKandidat =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(personident = unntak.personident)
                .isLatestIkkeKandidat()
        if (ikkeKandidat) {
            throw ConflictException("Failed to create Unntak: Person is not kandidat")
        }

        database.connection.use { connection ->
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
        val isPersonIkkeKandidat =
            dialogmotekandidatRepository.getDialogmotekandidatEndringer(personident = avvent.personident)
                .isLatestIkkeKandidat()
        if (isPersonIkkeKandidat) {
            throw ConflictException("Failed to create Avvent: Person is not kandidat")
        }

        database.connection.use { connection ->
            dialogmotekandidatVurderingRepository.createAvvent(connection = connection, avvent = avvent)
            connection.commit()
        }
    }

    suspend fun getAvvent(personident: Personident): List<Avvent> {
        val latestKandidatEndring = dialogmotekandidatService.getDialogmotekandidatEndringer(personident).latest()

        val latestAvvent = dialogmotekandidatVurderingRepository.getAvventList(personident = personident)
            .toAvventList()
            .maxByOrNull { it.createdAt }

        return if (
            latestAvvent != null &&
            latestKandidatEndring?.isAvventValidForDialogmotekandidatEndring(latestAvvent) == true
        ) {
            listOf(latestAvvent)
        } else emptyList()
    }

    suspend fun lukkAvvent(connection: Connection, avvent: Avvent) {
        dialogmotekandidatVurderingRepository.lukkAvvent(
            connection = connection,
            avvent = avvent,
        )
    }
}
