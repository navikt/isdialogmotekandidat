package no.nav.syfo.application

import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident
import java.sql.Connection

interface IDialogmotekandidatVurderingRepository {
    suspend fun getIkkeAktuellListForPerson(personident: Personident): List<DialogmotekandidatEndring.IkkeAktuell>
    suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: DialogmotekandidatEndring.IkkeAktuell)
    suspend fun createUnntak(connection: Connection, unntak: DialogmotekandidatEndring.Unntak)
    suspend fun createAvvent(connection: Connection, avvent: DialogmotekandidatEndring.Avvent)
    suspend fun lukkAvvent(connection: Connection, avvent: DialogmotekandidatEndring.Avvent)
    suspend fun getUnntakList(personident: Personident): List<DialogmotekandidatEndring.Unntak>
    suspend fun getAvventList(personident: Personident): List<DialogmotekandidatEndring.Avvent>
}
