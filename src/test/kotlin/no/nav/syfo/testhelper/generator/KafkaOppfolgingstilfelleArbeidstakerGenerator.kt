package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfelle
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePerson
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.util.*

fun generateKafkaOppfolgingstilfellePerson(
    arbeidstakerAtTilfelleEnd: Boolean = true,
    personIdentNumber: PersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    start: LocalDate = LocalDate.now().minusDays(1),
    oppfolgingstilfelleDurationInDays: Long,
    virksomhetsnummer: Virksomhetsnummer = VIRKSOMHETSNUMMER_DEFAULT,
    dodsdato: LocalDate? = null,
): KafkaOppfolgingstilfellePerson {
    return KafkaOppfolgingstilfellePerson(
        uuid = UUID.randomUUID().toString(),
        createdAt = nowUTC(),
        personIdentNumber = personIdentNumber.value,
        oppfolgingstilfelleList = listOf(
            KafkaOppfolgingstilfelle(
                arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
                start = start,
                end = start.plusDays(oppfolgingstilfelleDurationInDays),
                virksomhetsnummerList = listOf(
                    virksomhetsnummer.value,
                )
            ),
        ),
        referanseTilfelleBitUuid = UUID.randomUUID().toString(),
        referanseTilfelleBitInntruffet = nowUTC().minusDays(1),
        dodsdato = dodsdato,
    )
}
