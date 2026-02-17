package no.nav.syfo.testhelper.generator

import no.nav.syfo.api.CreateUnntakDTO
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.Personident

fun generateNewUnntakDTO(
    personident: Personident,
    arsak: DialogmotekandidatEndring.Unntak.Arsak = DialogmotekandidatEndring.Unntak.Arsak.FORVENTET_FRISKMELDING_INNEN_28UKER,
) = CreateUnntakDTO(
    personIdent = personident.value,
    arsak = arsak.name,
    beskrivelse = "Dette er en beskrivelse",
)
