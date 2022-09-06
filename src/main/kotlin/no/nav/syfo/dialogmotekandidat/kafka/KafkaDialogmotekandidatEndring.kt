package no.nav.syfo.dialogmotekandidat.kafka

import java.time.LocalDate
import java.time.OffsetDateTime

data class KafkaDialogmotekandidatEndring(
    val uuid: String,
    val createdAt: OffsetDateTime,
    val personIdentNumber: String,
    val kandidat: Boolean,
    val arsak: String,
    val unntakArsak: String?,
    val tilfelleStart: LocalDate?,
)
