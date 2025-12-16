package no.nav.syfo.application

import no.nav.syfo.domain.Avvent
import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Unntak
import no.nav.syfo.infrastructure.database.PAvvent
import no.nav.syfo.infrastructure.database.PIkkeAktuell
import no.nav.syfo.infrastructure.database.PUnntak
import java.sql.Connection

interface IDialogmotekandidatVurderingRepository {
    suspend fun getIkkeAktuellListForPerson(personident: Personident): List<PIkkeAktuell>
    suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: IkkeAktuell)
    suspend fun createUnntak(connection: Connection, unntak: Unntak)
    suspend fun createAvvent(connection: Connection, avvent: Avvent)
    suspend fun getUnntakList(personident: Personident): List<PUnntak>
    suspend fun getAvventList(personident: Personident): List<PAvvent>
}
