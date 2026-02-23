package no.nav.syfo.application

import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.isLatestIkkeKandidat
import no.nav.syfo.domain.latest
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository
import java.sql.Connection

class DialogmotekandidatVurderingService(
    private val database: DatabaseInterface,
    private val dialogmotekandidatService: DialogmotekandidatService,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val dialogmotekandidatRepository: DialogmotekandidatRepository,
    private val dialogmotekandidatVurderingRepository: IDialogmotekandidatVurderingRepository,
) {
    suspend fun createIkkeAktuell(
        ikkeAktuell: DialogmotekandidatEndring.IkkeAktuell,
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
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = ikkeAktuell,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
            )
            connection.commit()
        }
    }

    suspend fun getIkkeAktuellList(personident: Personident): List<DialogmotekandidatEndring.IkkeAktuell> =
        dialogmotekandidatVurderingRepository.getIkkeAktuellListForPerson(personident = personident)

    suspend fun createUnntak(
        unntak: DialogmotekandidatEndring.Unntak,
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
            dialogmotekandidatService.createDialogmotekandidatEndring(
                connection = connection,
                dialogmotekandidatEndring = unntak,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
            )
            connection.commit()
        }
    }

    suspend fun getUnntakList(personident: Personident): List<DialogmotekandidatEndring.Unntak> =
        dialogmotekandidatVurderingRepository.getUnntakList(personident = personident)

    suspend fun createAvvent(
        avvent: DialogmotekandidatEndring.Avvent,
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

    suspend fun getAvvent(personident: Personident): List<DialogmotekandidatEndring.Avvent> {
        val latestKandidatEndring = dialogmotekandidatService.getDialogmotekandidatEndringer(personident).latest()

        val latestAvvent = dialogmotekandidatVurderingRepository.getAvventList(personident = personident)
            .maxByOrNull { it.createdAt }
        val isActiveAvvent = latestAvvent != null &&
            latestKandidatEndring?.kandidat == true &&
            latestAvvent.createdAt.isAfter(latestKandidatEndring.createdAt) &&
            !latestAvvent.isLukket

        return if (isActiveAvvent) listOf(latestAvvent) else emptyList()
    }

    suspend fun lukkAvvent(connection: Connection, avvent: DialogmotekandidatEndring.Avvent) {
        dialogmotekandidatVurderingRepository.lukkAvvent(
            connection = connection,
            avvent = avvent,
        )
    }
}
