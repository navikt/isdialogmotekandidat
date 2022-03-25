package no.nav.syfo.util

import java.time.OffsetDateTime
import java.time.ZoneOffset

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)
