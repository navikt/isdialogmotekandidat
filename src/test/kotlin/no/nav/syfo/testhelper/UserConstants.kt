package no.nav.syfo.testhelper

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val ARBEIDSTAKER_PERSONIDENTNUMBER = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_PERSONIDENTNUMBER_NOT_KANDIDAT = PersonIdentNumber("12345678912".replace("2", "8"))
    val ARBEIDSTAKER_PERSONIDENTNUMBER_OLD_KANDIDAT = PersonIdentNumber("12345678912".replace("3", "7"))
    val ARBEIDSTAKER_PERSONIDENTNUMBER_DOD = PersonIdentNumber("12345678912".replace("4", "6"))
    val PERSONIDENTNUMBER_VEILEDER_NO_ACCESS = PersonIdentNumber(ARBEIDSTAKER_PERSONIDENTNUMBER.value.replace("2", "3"))
    val ARBEIDSTAKER_2_PERSONIDENTNUMBER = PersonIdentNumber(ARBEIDSTAKER_PERSONIDENTNUMBER.value.replace("2", "1"))
    val ARBEIDSTAKER_3_PERSONIDENTNUMBER = PersonIdentNumber("12345678913")
    val FEILENDE_PERSONIDENTNUMBER = PersonIdentNumber("12345670000")

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer("987654321")
    const val VEILEDER_IDENT = "Z999999"
}
