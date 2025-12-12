package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS
import no.nav.syfo.domain.isDialogmotekandidat
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringFerdigstilt
import no.nav.syfo.testhelper.generator.generateDialogmotekandidatEndringStoppunkt
import no.nav.syfo.testhelper.generator.generateOppfolgingstilfelle
import no.nav.syfo.util.defaultZoneOffset
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OppfolgingstilfelleTest {
    private val personident = ARBEIDSTAKER_PERSONIDENTNUMBER

    @Test
    fun `isDialogmotekandidat returns false if duration is less than DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 1,
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `isDialogmotekandidat returns true if duration is equal to DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `isDialogmotekandidat returns true if duration is greater than DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS and list of DialogmoteKandidatEndring is empty`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `isDialogmotekandidat returns false if latestDialogmoteFerdigstilt after tilfelle start`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.atStartOfDay().atOffset(defaultZoneOffset)
                .plusDays(DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS - 10),
        )
        assertFalse(result)
    }

    @Test
    fun `isDialogmotekandidat returns true if latestDialogmoteFerdigstilt before tilfelle start`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS + 1,
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = emptyList(),
            latestDialogmoteFerdigstilt = latestOppfolgingstilfelle.tilfelleStart.atStartOfDay().atOffset(defaultZoneOffset).minusDays(1),
        )
        assertTrue(result)
    }

    @Test
    fun `isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and within oppfolgingstilfelle`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.minusDays(1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `isDialogmotekandidat returns true if latest DialogmoteKandidatEndring is Kandidat, but is before tilfelleStart`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleStart`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.atStartOfDay().atOffset(defaultZoneOffset)
        )
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(personident).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring, ferdigstiltEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `isDialogmotekandidat returns false if latest DialogmoteKandidatEndring is Kandidat and is equal to tilfelleEnd`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.atStartOfDay().atOffset(defaultZoneOffset)
        )
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(personident).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring, ferdigstiltEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }

    @Test
    fun `isDialogmotekandidat returns true if latest DialogmoteKandidatEndring with kandidat==true is before tilfelleStart`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleStart.minusDays(1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(personident).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring, ferdigstiltEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertTrue(result)
    }

    @Test
    fun `isDialogmotekandidat returns false if latest DialogmoteKandidatEndring with kandidat==true is after tilfelleStart (and after tilfelleEnd)`() {
        val latestOppfolgingstilfelle = generateOppfolgingstilfelle(
            arbeidstakerPersonIdent = personident,
            oppfolgingstilfelleDurationInDays = DIALOGMOTEKANDIDAT_STOPPUNKT_DURATION_DAYS,
        )
        val stoppunktEndring = generateDialogmotekandidatEndringStoppunkt(personident).copy(
            createdAt = latestOppfolgingstilfelle.tilfelleEnd.plusDays(1).atStartOfDay().atOffset(defaultZoneOffset)
        )
        val ferdigstiltEndring = generateDialogmotekandidatEndringFerdigstilt(personident).copy(
            createdAt = stoppunktEndring.createdAt.plusDays(1)
        )
        val result = latestOppfolgingstilfelle.isDialogmotekandidat(
            dialogmotekandidatEndringList = listOf(stoppunktEndring, ferdigstiltEndring),
            latestDialogmoteFerdigstilt = null,
        )
        assertFalse(result)
    }
}
