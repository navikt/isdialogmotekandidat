package no.nav.syfo.testhelper

import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime

fun OffsetDateTime.shouldBeEqualToOffsetDateTime(expected: OffsetDateTime) {
    this.toInstant().toEpochMilli() shouldBeEqualTo expected.toInstant().toEpochMilli()
}
