package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfelleArbeidstaker
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.defaultZoneOffset
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateKafkaOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdentNumber: PersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    oppfolgingstilfelleDurationInDays: Long,
    virksomhetsnummer: Virksomhetsnummer = VIRKSOMHETSNUMMER_DEFAULT,
): KafkaOppfolgingstilfelleArbeidstaker {
    val start = LocalDate.now().minusDays(1)
    return KafkaOppfolgingstilfelleArbeidstaker(
        uuid = UUID.randomUUID().toString(),
        createdAt = OffsetDateTime.now(defaultZoneOffset),
        personIdentNumber = arbeidstakerPersonIdentNumber.value,
        oppfolgingstilfelleList = listOf(
            KafkaOppfolgingstilfelle(
                start = start,
                end = start.plusDays(oppfolgingstilfelleDurationInDays),
                virksomhetsnummerList = listOf(
                    virksomhetsnummer.value,
                )
            ),
        ),
        referanseTilfelleBitUuid = UUID.randomUUID().toString(),
        referanseTilfelleBitInntruffet = OffsetDateTime.now(defaultZoneOffset).minusDays(1),
    )
}
