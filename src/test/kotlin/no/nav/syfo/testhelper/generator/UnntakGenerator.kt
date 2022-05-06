package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.UnntakArsak
import no.nav.syfo.unntak.api.domain.CreateUnntakDTO

fun generateNewUnntakDTO(
    personIdent: PersonIdentNumber,
) = CreateUnntakDTO(
    personIdent = personIdent.value,
    arsak = UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER.name,
    beskrivelse = "Dette er en beskrivelse",
)
