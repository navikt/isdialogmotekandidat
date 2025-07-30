package no.nav.syfo.application

import no.nav.syfo.domain.IkkeAktuell
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.PIkkeAktuell
import java.sql.Connection

interface IIkkeAktuellRepository {
    suspend fun getIkkeAktuellListForPerson(personIdent: Personident): List<PIkkeAktuell>
    suspend fun createIkkeAktuell(connection: Connection, commit: Boolean, ikkeAktuell: IkkeAktuell): Unit
}
