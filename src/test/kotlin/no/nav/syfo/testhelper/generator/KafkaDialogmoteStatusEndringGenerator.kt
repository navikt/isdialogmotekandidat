package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusEndringType
import no.nav.syfo.domain.Personident
import java.time.OffsetDateTime

fun generateKDialogmoteStatusEndring(
    personIdentNumber: Personident,
    statusEndringType: DialogmoteStatusEndringType,
    moteTidspunkt: OffsetDateTime,
    endringsTidspunkt: OffsetDateTime,
): KDialogmoteStatusEndring {
    val kDialogmoteStatusEndring = KDialogmoteStatusEndring()
    kDialogmoteStatusEndring.setPersonIdent(personIdentNumber.value)
    kDialogmoteStatusEndring.setStatusEndringType(statusEndringType.name)
    kDialogmoteStatusEndring.setDialogmoteTidspunkt(moteTidspunkt.toInstant())
    kDialogmoteStatusEndring.setStatusEndringTidspunkt(endringsTidspunkt.toInstant())

    return kDialogmoteStatusEndring
}
