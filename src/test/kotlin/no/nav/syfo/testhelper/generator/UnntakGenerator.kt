package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.UnntakArsak
import no.nav.syfo.unntak.api.domain.NewUnntakDTO

fun generateNewUnntakDTO(
    personIdent: PersonIdentNumber,
) = NewUnntakDTO(
    personIdent = personIdent.value,
    arsak = UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER.name
)
