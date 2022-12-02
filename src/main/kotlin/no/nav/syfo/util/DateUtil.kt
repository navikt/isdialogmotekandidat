package no.nav.syfo.util

import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = this.atZoneSameInstant(
    ZoneId.of("Europe/Oslo")
).toLocalDateTime()

fun tomorrow() = LocalDate.now().plusDays(1)
