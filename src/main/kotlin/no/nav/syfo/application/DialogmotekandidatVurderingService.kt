package no.nav.syfo.application

import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.isLatestIkkeKandidat
import no.nav.syfo.domain.latest
import no.nav.syfo.infrastructure.database.dialogmotekandidat.DialogmotekandidatRepository

class DialogmotekandidatVurderingService(
    private val transactionManager: ITransactionManager,
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
        val latestOppfolgingstilfelleArbeidstaker = oppfolgingstilfelleService.getLatestOppfolgingstilfelle(
            arbeidstakerPersonIdent = ikkeAktuell.personident,
            veilederToken = veilederToken,
            callId = callId,
        )
        transactionManager.run { transaction ->
            val ikkeKandidat =
                dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                    transaction = transaction,
                    personident = ikkeAktuell.personident,
                )
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create IkkeAktuell: Person is not kandidat")
            }
            dialogmotekandidatVurderingRepository.createIkkeAktuell(transaction = transaction, ikkeAktuell = ikkeAktuell)
            dialogmotekandidatService.createDialogmotekandidatEndring(
                transaction = transaction,
                dialogmotekandidatEndring = ikkeAktuell,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
            )
            lukkAlleEksisterendeAvvent(transaction, ikkeAktuell.personident)
        }
    }

    fun getIkkeAktuellList(personident: Personident): List<DialogmotekandidatEndring.IkkeAktuell> =
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

        transactionManager.run { transaction ->
            val ikkeKandidat =
                dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                    transaction = transaction,
                    personident = unntak.personident,
                )
                    .isLatestIkkeKandidat()
            if (ikkeKandidat) {
                throw ConflictException("Failed to create Unntak: Person is not kandidat")
            }
            dialogmotekandidatVurderingRepository.createUnntak(transaction = transaction, unntak = unntak)
            dialogmotekandidatService.createDialogmotekandidatEndring(
                transaction = transaction,
                dialogmotekandidatEndring = unntak,
                tilfelleStart = latestOppfolgingstilfelleArbeidstaker?.tilfelleStart,
            )
            lukkAlleEksisterendeAvvent(transaction, unntak.personident)
        }
    }

    fun getUnntakList(personident: Personident): List<DialogmotekandidatEndring.Unntak> =
        dialogmotekandidatVurderingRepository.getUnntakList(personident = personident)

    suspend fun createAvvent(
        avvent: DialogmotekandidatEndring.Avvent,
    ) {
        transactionManager.run { transaction ->
            val isPersonIkkeKandidat =
                dialogmotekandidatRepository.getDialogmotekandidatEndringer(
                    transaction = transaction,
                    personident = avvent.personident,
                )
                    .isLatestIkkeKandidat()
            if (isPersonIkkeKandidat) {
                throw ConflictException("Failed to create Avvent: Person is not kandidat")
            }
            lukkAlleEksisterendeAvvent(transaction, avvent.personident)
            dialogmotekandidatVurderingRepository.createAvvent(transaction = transaction, avvent = avvent)
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

    fun lukkAvvent(transaction: ITransaction, avvent: DialogmotekandidatEndring.Avvent) {
        dialogmotekandidatVurderingRepository.lukkAvvent(
            transaction = transaction,
            avvent = avvent,
        )
    }

    private fun lukkAlleEksisterendeAvvent(transaction: ITransaction, personident: Personident) {
        dialogmotekandidatVurderingRepository.getAvventList(personident = personident)
            .filter { !it.isLukket }
            .forEach { existingAvvent ->
                dialogmotekandidatVurderingRepository.lukkAvvent(
                    transaction = transaction,
                    avvent = existingAvvent,
                )
            }
    }
}
