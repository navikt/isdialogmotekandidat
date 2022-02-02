package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstakerList
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstaker

class OppfolgingstilfelleService(
    private val database: DatabaseInterface,
) {
    fun getSisteOppfolgingstilfelle(arbeidstakerPersonIdent: PersonIdentNumber): OppfolgingstilfelleArbeidstaker? {
        return database.getOppfolgingstilfelleArbeidstakerList(arbeidstakerPersonIdent = arbeidstakerPersonIdent)
            .firstOrNull()
            ?.toOppfolgingstilfelleArbeidstaker()
    }
}
