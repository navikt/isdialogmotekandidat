package no.nav.syfo.domain

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class DialogmoteStatusEndring private constructor(
    val personIdentNumber: Personident,
    val type: Type,
    val createdAt: OffsetDateTime,
    val moteTidspunkt: OffsetDateTime,
    val statusTidspunkt: OffsetDateTime,
) {

    /**
     * Returnerer true hvis endringstypen for dialogmøte er relevant for dialogmøtekandidatstatusen
     */
    fun isRelevant() =
        this.type == Type.FERDIGSTILT || this.type == Type.LUKKET || this.type == Type.INNKALT

    enum class Type {
        INNKALT,
        AVLYST,
        FERDIGSTILT,
        NYTT_TID_STED,
        LUKKET,
    }

    companion object {
        fun create(kafkaDialogmoteStatusEndring: KDialogmoteStatusEndring) = DialogmoteStatusEndring(
            personIdentNumber = Personident(kafkaDialogmoteStatusEndring.getPersonIdent()),
            type = Type.valueOf(kafkaDialogmoteStatusEndring.getStatusEndringType()),
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
