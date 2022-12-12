package no.nav.syfo.oppfolgingstilfelle.database

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class POppfolgingstilfelleArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val tilfelleGenerert: OffsetDateTime,
    val personIdent: PersonIdentNumber,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val referanseTilfelleBitUUID: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

fun List<POppfolgingstilfelleArbeidstaker>.toOppfolgingstilfelleArbeidstakerList() = this.map {
    it.toOppfolgingstilfelleArbeidstaker()
}

fun POppfolgingstilfelleArbeidstaker.toOppfolgingstilfelleArbeidstaker() = OppfolgingstilfelleArbeidstaker(
    uuid = this.uuid,
    createdAt = this.createdAt,
    tilfelleGenerert = this.tilfelleGenerert,
    personIdent = this.personIdent,
    tilfelleStart = this.tilfelleStart,
    tilfelleEnd = this.tilfelleEnd,
    referanseTilfelleBitUuid = this.referanseTilfelleBitUUID,
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
)

fun POppfolgingstilfelleArbeidstaker.toOppfolgingstilfelle() = Oppfolgingstilfelle(
    personIdent = personIdent,
    tilfelleStart = tilfelleStart,
    tilfelleEnd = tilfelleEnd,
    arbeidstakerAtTilfelleEnd = true,
    virksomhetsnummerList = emptyList(),
)
