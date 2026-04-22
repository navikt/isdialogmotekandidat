package no.nav.syfo.application

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident

interface IDialogmotekandidatVurderingRepository {
    fun getIkkeAktuellListForPerson(personident: Personident): List<DialogmotekandidatEndring.IkkeAktuell>
    fun createIkkeAktuell(transaction: ITransaction, ikkeAktuell: DialogmotekandidatEndring.IkkeAktuell)
    fun createUnntak(transaction: ITransaction, unntak: DialogmotekandidatEndring.Unntak)
    fun getUnntakList(personident: Personident): List<DialogmotekandidatEndring.Unntak>
}
