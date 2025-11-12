package no.nav.syfo.util

import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = this.atZoneSameInstant(
    ZoneId.of("Europe/Oslo")
).toLocalDateTime()

fun OffsetDateTime.toLocalDateOslo(): LocalDate = this.toLocalDateTimeOslo().toLocalDate()

fun tomorrow(): LocalDate = LocalDate.now().plusDays(1)

fun LocalDate.isAfterOrEqual(another: LocalDate) = this.isAfter(another) || this == another

fun LocalDate.isBeforeOrEqual(another: LocalDate) = this.isBefore(another) || this == another

fun LocalDate.toOffsetDatetime(): OffsetDateTime = this.atStartOfDay().atOffset(defaultZoneOffset)
