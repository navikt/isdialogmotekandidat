package no.nav.syfo.dialogmotekandidat.kafka

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.syfo.domain.DialogmotekandidatEndring
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.DialogmotekandidatEndringRecord
import no.nav.syfo.infrastructure.kafka.dialogmotekandidat.KafkaDialogmotekandidatEndringSerializer
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.util.configuredJacksonMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class KafkaDialogmotekandidatEndringSerializerTest {
    private val serializer = KafkaDialogmotekandidatEndringSerializer()
    private val mapper = configuredJacksonMapper()
    private val personident = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
    private val tilfelleStart = LocalDate.of(2026, 1, 1)

    @Test
    fun `serializes all ikke aktuell reasons without changing existing fields`() {
        DialogmotekandidatEndring.IkkeAktuell.Arsak.entries.forEach { arsak ->
            val endring = DialogmotekandidatEndring.ikkeAktuell(
                personident = personident,
                createdBy = UserConstants.VEILEDER_IDENT,
                arsak = arsak,
                beskrivelse = null,
            )
            val record = DialogmotekandidatEndringRecord.from(endring, tilfelleStart)
            val json = mapper.readTree(serializer.serialize("topic", record))

            assertEquals(arsak.name, json["ikkeAktuellArsak"].asText())
            assertEquals(endring.uuid.toString(), json["uuid"].asText())
            assertEquals(
                endring.createdAt,
                OffsetDateTime.parse(json["createdAt"].asText()),
            )
            assertEquals(personident.value, json["personIdentNumber"].asText())
            assertFalse(json["kandidat"].asBoolean())
            assertEquals("IKKE_AKTUELL", json["arsak"].asText())
            assertEquals(tilfelleStart.toString(), json["tilfelleStart"].asText())
            assertTrue(json["unntakArsak"].isNull)
            assertTrue(json["unntakVeilederident"].isNull)
            assertEquals(endring.createdBy, json["ikkeAktuellVeilederident"].asText())
        }
    }

    @Test
    fun `other changes have no ikke aktuell reason and preserve unntak fields`() {
        val unntak = DialogmotekandidatEndring.unntak(
            personident = personident,
            createdBy = UserConstants.VEILEDER_IDENT,
            arsak = DialogmotekandidatEndring.Unntak.Arsak.MEDISINSKE_GRUNNER,
            beskrivelse = null,
        )
        val endringer = listOf(
            unntak,
            DialogmotekandidatEndring.createKandidat(personident),
            DialogmotekandidatEndring.ferdigstiltDialogmote(personident),
            DialogmotekandidatEndring.lukketDialogmote(personident),
            DialogmotekandidatEndring.lukket(personident),
        )

        endringer.forEach { endring ->
            val record = DialogmotekandidatEndringRecord.from(endring, tilfelleStart = null)
            val json = mapper.readTree(serializer.serialize("topic", record))

            assertTrue(json["ikkeAktuellArsak"].isNull)
            assertTrue(json["ikkeAktuellVeilederident"].isNull)
            assertEquals(endring.arsak.name, json["arsak"].asText())
            assertEquals(endring.kandidat, json["kandidat"].asBoolean())
            if (endring == unntak) {
                assertEquals(unntak.unntakArsak.name, json["unntakArsak"].asText())
                assertEquals(unntak.createdBy, json["unntakVeilederident"].asText())
            } else {
                assertTrue(json["unntakArsak"].isNull)
                assertTrue(json["unntakVeilederident"].isNull)
            }
        }
    }

    @Test
    fun `reads older records without ikke aktuell reason`() {
        val record = DialogmotekandidatEndringRecord.from(
            dialogmoteKandidatEndring = DialogmotekandidatEndring.ikkeAktuell(
                personident = personident,
                createdBy = UserConstants.VEILEDER_IDENT,
                arsak = DialogmotekandidatEndring.IkkeAktuell.Arsak.FRISKMELDT,
                beskrivelse = null,
            ),
            tilfelleStart = tilfelleStart,
        )
        val json = mapper.readTree(serializer.serialize("topic", record)) as ObjectNode
        json.remove("ikkeAktuellArsak")
        json.remove("ikkeAktuellVeilederident")

        val deserialized = mapper.treeToValue(json, DialogmotekandidatEndringRecord::class.java)

        assertEquals(record.copy(ikkeAktuellArsak = null, ikkeAktuellVeilederident = null), deserialized)
    }
}
