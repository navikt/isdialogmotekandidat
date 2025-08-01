package no.nav.syfo.domain

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class DialogmoteStatusEndringType {
    INNKALT,
    AVLYST,
    FERDIGSTILT,
    NYTT_TID_STED,
    LUKKET,
}

data class DialogmoteStatusEndring private constructor(
    val personIdentNumber: Personident,
    val type: DialogmoteStatusEndringType,
    val createdAt: OffsetDateTime,
    val moteTidspunkt: OffsetDateTime,
    val statusTidspunkt: OffsetDateTime,
) {
    companion object {
        fun create(kafkaDialogmoteStatusEndring: KDialogmoteStatusEndring) = DialogmoteStatusEndring(
            personIdentNumber = Personident(kafkaDialogmoteStatusEndring.getPersonIdent()),
            type = DialogmoteStatusEndringType.valueOf(kafkaDialogmoteStatusEndring.getStatusEndringType()),
            createdAt = OffsetDateTime.now(),
            moteTidspunkt = OffsetDateTime.ofInstant(
                kafkaDialogmoteStatusEndring.getDialogmoteTidspunkt(),
                ZoneOffset.UTC,
            ),
            statusTidspunkt = OffsetDateTime.ofInstant(
                kafkaDialogmoteStatusEndring.getStatusEndringTidspunkt(),
                ZoneOffset.UTC,
            ),
        )
    }
}
