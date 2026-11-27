package de.dokumentenpilot.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class FamilienArchivAustauschTest {
    private val id = "aec322ad-3fdf-41f6-ab39-0cee989fb333.dp"
    private val eintrag = FamilienArchivAustausch.Eintrag(id, "Synthetische Probe", true, false)
    private val pdf = "%PDF-1.4\nSynthetische Probe".toByteArray()

    @Test fun unbekannteIdErreichtEntschluesselungNicht() {
        val protokoll = FamilienArchivAustausch({ listOf(eintrag) },
            { _, _, _ -> error("Fremde ID") }, { _, _ -> false }, { _, _, _ -> null }, { _, _, _ -> })
        val eingang = eingang { writeByte(0); writeUTF("../fremd.dp") }
        val ausgang = DataOutputStream(ByteArrayOutputStream())
        assertFalse(protokoll.senden("peer", eingang, ausgang, { true }, {}))
    }

    @Test fun widerrufBeendetLaufendeVorschau() {
        val protokoll = FamilienArchivAustausch({ listOf(eintrag) },
            { _, _, liefern -> liefern(pdf); true }, { _, _ -> false }, { _, _, _ -> null }, { _, _, _ -> })
        val bytes = ByteArrayOutputStream()
        assertFalse(protokoll.senden("peer", eingang { writeByte(0); writeUTF(id); writeByte(1) },
            DataOutputStream(bytes), { true }, {}))
        val antwort = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, antwort.readInt())
        assertEquals(id, antwort.readUTF())
        assertEquals(eintrag.titel, antwort.readUTF())
        assertTrue(antwort.readBoolean())
        assertFalse(antwort.readBoolean())
        assertEquals(pdf.size, antwort.readInt())
        assertArrayEquals(pdf, ByteArray(pdf.size).also(antwort::readFully))
        assertFalse(antwort.readBoolean())
    }

    @Test fun empfangPrueftRechtUndLoeschtKlartextpuffer() {
        var puffer: ByteArray? = null
        val protokoll = FamilienArchivAustausch({ emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _ -> angebote.single().id },
            { angebot, daten, verlaengern ->
                assertEquals(eintrag, angebot)
                assertArrayEquals(pdf, daten)
                assertTrue(verlaengern())
                puffer = daten
            })
        val ausgang = ByteArrayOutputStream()
        val eingang = eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(eintrag.suchbar)
            writeBoolean(eintrag.assistent)
            writeInt(pdf.size)
            write(pdf)
            writeBoolean(true)
            writeBoolean(true)
        }
        assertTrue(protokoll.empfangen("peer", eingang, DataOutputStream(ausgang), { true }, {}))
        assertArrayEquals(ByteArray(pdf.size), checkNotNull(puffer))
        val antwort = DataInputStream(ByteArrayInputStream(ausgang.toByteArray()))
        assertEquals(0, antwort.readUnsignedByte())
        assertEquals(id, antwort.readUTF())
        assertEquals(1, antwort.readUnsignedByte())
        assertEquals(1, antwort.readUnsignedByte())
        assertEquals(0, antwort.readUnsignedByte())
    }

    @Test fun verweigerteVorschauUebergibtKeinePdfAnOberflaeche() {
        val protokoll = FamilienArchivAustausch({ emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _ -> angebote.single().id }, { _, _, _ -> error("Vorschau trotz Widerruf") })
        val eingang = eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(true)
            writeBoolean(false)
            writeInt(pdf.size)
            write(pdf)
            writeBoolean(false)
        }
        assertFalse(protokoll.empfangen("peer", eingang, DataOutputStream(ByteArrayOutputStream()), { true }, {}))
    }

    private fun eingang(schreiben: DataOutputStream.() -> Unit): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.schreiben() }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
}
