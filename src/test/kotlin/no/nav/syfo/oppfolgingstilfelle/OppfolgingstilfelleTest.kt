package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.domain.isDialogmotekandidat
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.defaultZoneOffset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*

class OppfolgingstilfelleTest {
    private val personIdent = ARBEIDSTAKER_PERSONIDENTNUMBER

    @Test
    fun `duration less than cutoff returns false when no endringer`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `duration == cutoff returns true when no endringer`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun durationGreaterThanCutoffReturnsTrueWhenNoEndringer() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `latest kandidat endring after tilfelleStart returns false`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.toOffsetDatetime()
                .plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
        )
        assertFalse(result)
    }

    @Test
    fun `latest kandidat endring before tilfelleStart returns true`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = emptyList()

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.toOffsetDatetime().minusDays(1),
        )
        assertTrue(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring is Kandidat and within oppfolgingstilfelle returns false`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.minusDays(1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring is Kandidat, but is before tilfelleStart returns true`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleStart returns false`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
            dialogmotekandidatEndringFerdigstilt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleEnd returns false`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
            dialogmotekandidatEndringFerdigstilt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring with kandidat==true is before tilfelleStart returns true`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
            dialogmotekandidatEndringFerdigstilt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `latest DialogmoteKandidatEndring with kandidat==true is after tilfelleStart (and after tilfelleEnd) returns false`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personIdent,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val dialogmotekandidatEndringStoppunkt = generateDialogmotekandidatEndringStoppunkt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.plusDays(1).atStartOfDay()
                .atOffset(defaultZoneOffset)
        )
        val dialogmotekandidatEndringFerdigstilt = generateDialogmotekandidatEndringFerdigstilt(
            personIdentNumber = personIdent,
        ).copy(
            createdAt = dialogmotekandidatEndringStoppunkt.createdAt.plusDays(1)
        )

        val dialogmotekandidatEndringList: List<DialogmotekandidatEndring> = listOf(
            dialogmotekandidatEndringStoppunkt,
            dialogmotekandidatEndringFerdigstilt,
        )

        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = dialogmotekandidatEndringList,
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }
}

fun LocalDate.toOffsetDatetime() = OffsetDateTime.of(this, LocalTime.NOON, ZoneOffset.UTC)
