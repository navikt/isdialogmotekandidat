package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.api.domain.CreateUnntakDTO
import no.nav.syfo.unntak.domain.UnntakArsak

fun generateNewUnntakDTO(
    personIdent: PersonIdentNumber,
    arsak: UnntakArsak = UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER,
) = CreateUnntakDTO(
    personIdent = personIdent.value,
    arsak = arsak.name,
    beskrivelse = "Dette er en beskrivelse",
)
