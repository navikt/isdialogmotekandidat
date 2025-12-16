package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.Personident
import no.nav.syfo.api.CreateUnntakDTO
import no.nav.syfo.domain.UnntakArsak

fun generateNewUnntakDTO(
    personident: Personident,
    arsak: UnntakArsak = UnntakArsak.FORVENTET_FRISKMELDING_INNEN_28UKER,
) = CreateUnntakDTO(
    personIdent = personident.value,
    arsak = arsak.name,
    beskrivelse = "Dette er en beskrivelse",
)
