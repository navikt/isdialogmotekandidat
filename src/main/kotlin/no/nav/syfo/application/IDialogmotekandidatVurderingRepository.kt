package no.nav.syfo.application

import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import no.nav.syfo.infrastructure.database.PIkkeAktuell
import no.nav.syfo.infrastructure.database.PUnntak
import java.sql.Connection

interface IDialogmotekandidatVurderingRepository {
    suspend fun getIkkeAktuellListForPerson(personIdent: Personident): List<PIkkeAktuell>
    suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: IkkeAktuell): Unit
    suspend fun createUnntak(connection: Connection, unntak: Unntak): Unit
    suspend fun getUnntakList(personIdent: Personident): List<PUnntak>
}
