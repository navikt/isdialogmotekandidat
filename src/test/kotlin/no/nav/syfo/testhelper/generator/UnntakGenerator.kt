package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.api.domain.NewUnntakDTO

fun generateNewUnntakDTO(
    personIdent: PersonIdentNumber,
) = NewUnntakDTO(personIdent = personIdent.value)
