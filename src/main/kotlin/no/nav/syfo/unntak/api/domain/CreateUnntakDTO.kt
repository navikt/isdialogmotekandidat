package no.nav.syfo.unntak.api.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.domain.Unntak
import no.nav.syfo.unntak.domain.UnntakArsak
import no.nav.syfo.util.nowUTC
import java.util.*

data class CreateUnntakDTO(
    val personIdent: String,
    val arsak: String,
    val begrunnelse: String?,
)

fun CreateUnntakDTO.toUnntak(
    createdByIdent: String,
) = Unntak(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    createdBy = createdByIdent,
    personIdent = PersonIdentNumber(this.personIdent),
    arsak = UnntakArsak.valueOf(this.arsak),
    begrunnelse = this.begrunnelse,
)
