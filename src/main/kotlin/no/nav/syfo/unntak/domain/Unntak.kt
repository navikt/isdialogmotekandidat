package no.nav.syfo.unntak.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.unntak.api.domain.UnntakDTO
import java.time.OffsetDateTime
import java.util.*

enum class UnntakArsak {
    MEDISINSKE_GRUNNER,
    INNLEGGELSE_INSTITUSJON,
    FORVENTET_FRISKMELDING_INNEN_28UKER,
    DOKUMENTERT_TILTAK_FRISKMELDING,
    ARBEIDSFORHOLD_OPPHORT,
}

data class Unntak(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val personIdent: PersonIdentNumber,
    val arsak: UnntakArsak,
    val begrunnelse: String?,
)

fun List<Unntak>.toUnntakDTOList() = this.map { unntak ->
    UnntakDTO(
        uuid = unntak.uuid.toString(),
        createdAt = unntak.createdAt.toLocalDateTime(),
        createdBy = unntak.createdBy,
        personIdent = unntak.personIdent.value,
        arsak = unntak.arsak.name,
        begrunnelse = unntak.begrunnelse,
    )
}
