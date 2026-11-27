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
            { _, _, _ -> error("Fremde ID") }, { _, _ -> false }, { _, _, _, _ -> null }, { _, _, _ -> })
        val eingang = eingang { writeByte(0); writeUTF("../fremd.dp") }
        val ausgang = DataOutputStream(ByteArrayOutputStream())
        assertFalse(protokoll.senden("peer", eingang, ausgang, { true }, {}))
    }

    @Test fun widerrufBeendetLaufendeVorschau() {
        val protokoll = FamilienArchivAustausch({ listOf(eintrag) },
            { _, _, liefern -> liefern(pdf); true }, { _, _ -> false }, { _, _, _, _ -> null }, { _, _, _ -> })
        val bytes = ByteArrayOutputStream()
        assertFalse(protokoll.senden("peer", eingang { writeByte(0); writeUTF(id); writeByte(1) },
            DataOutputStream(bytes), { true }, {}))
        val antwort = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, antwort.readInt())
        assertEquals(id, antwort.readUTF())
        assertEquals(eintrag.titel, antwort.readUTF())
        assertTrue(antwort.readBoolean())
        assertFalse(antwort.readBoolean())
        assertFalse(antwort.readBoolean())
        assertEquals(0, antwort.readUnsignedByte())
        assertFalse(antwort.readBoolean())
        assertEquals(0, antwort.readUnsignedByte())
        assertEquals(pdf.size, antwort.readInt())
        assertArrayEquals(pdf, ByteArray(pdf.size).also(antwort::readFully))
        assertFalse(antwort.readBoolean())
    }

    @Test fun empfangPrueftRechtUndLoeschtKlartextpuffer() {
        var puffer: ByteArray? = null
        val protokoll = FamilienArchivAustausch({ emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _, _ -> FamilienArchivAustausch.Anfrage(angebote.single().id) },
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
            writeBoolean(false)
            writeByte(0)
            writeBoolean(false)
            writeByte(0)
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
            { _, angebote, _, _ -> FamilienArchivAustausch.Anfrage(angebote.single().id) }, { _, _, _ -> error("Vorschau trotz Widerruf") })
        val eingang = eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(true)
            writeBoolean(false)
            writeBoolean(false)
            writeByte(0)
            writeBoolean(false)
            writeByte(0)
            writeInt(pdf.size)
            write(pdf)
            writeBoolean(false)
        }
        assertFalse(protokoll.empfangen("peer", eingang, DataOutputStream(ByteArrayOutputStream()), { true }, {}))
    }

    @Test fun umbenennenErfordertAngebotUndAktuelleFreigabe() {
        for (angeboten in listOf(false, true)) {
            for (weiterhinErlaubt in listOf(false, true)) {
                var versuche = 0
                val protokoll = FamilienArchivAustausch(
                    { listOf(eintrag.copy(umbenennbar = angeboten)) },
                    { _, _, _ -> error("Umbenennen darf keine PDF lesen") },
                    { _, _ -> false }, { _, _, _, _ -> null }, { _, _, _ -> },
                    umbenennen = { peer, dokument, alt, titel ->
                        versuche++
                        assertEquals("peer", peer)
                        assertEquals(id, dokument)
                        assertEquals(eintrag.titel, alt)
                        assertEquals("Synthetischer neuer Titel", titel)
                        weiterhinErlaubt
                    })
                val bytes = ByteArrayOutputStream()
                assertTrue(protokoll.senden("peer", eingang {
                    writeByte(2)
                    writeUTF(id)
                    writeUTF("Synthetischer neuer Titel")
                }, DataOutputStream(bytes), { true }, {}))
                assertEquals(if (angeboten) 1 else 0, versuche)
                assertEquals(angeboten && weiterhinErlaubt, bytes.toByteArray().last() == 1.toByte())
            }
        }
    }

    @Test fun empfaengerMeldetBestaetigungDesEigentuemers() {
        var bestaetigt: Boolean? = null
        val neuerTitel = "Neue synthetische Probe"
        val protokoll = FamilienArchivAustausch(
            { emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _, _ -> FamilienArchivAustausch.Anfrage(angebote.single().id, neuerTitel) },
            { _, _, _ -> error("Umbenennen darf keine PDF laden") },
            aenderungGemeldet = { bestaetigt = it })
        val ausgang = ByteArrayOutputStream()
        assertTrue(protokoll.empfangen("peer", eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(true)
            writeBoolean(false)
            writeBoolean(true)
            writeByte(0)
            writeBoolean(false)
            writeByte(0)
            writeBoolean(true)
        }, DataOutputStream(ausgang), { true }, {}))
        assertEquals(true, bestaetigt)
        val anfrage = DataInputStream(ByteArrayInputStream(ausgang.toByteArray()))
        assertEquals(2, anfrage.readUnsignedByte())
        assertEquals(id, anfrage.readUTF())
        assertEquals(neuerTitel, anfrage.readUTF())
    }

    @Test fun verschiebenLehntNichtAngeboteneZieleAb() {
        for (ziel in listOf("Freigegeben", "Privat")) {
            val protokoll = FamilienArchivAustausch(
                { listOf(eintrag.copy(zielKategorien = listOf("Freigegeben"), quellKategorie = "Quelle")) },
                { _, _, _ -> error("Verschieben darf keine PDF lesen") },
                { _, _ -> false }, { _, _, _, _ -> null }, { _, _, _ -> },
                verschieben = { peer, dokument, quelle, angefragt ->
                    assertEquals("peer", peer)
                    assertEquals(id, dokument)
                    assertEquals("Quelle", quelle)
                    assertEquals("Freigegeben", angefragt)
                    true
                })
            val ausgang = ByteArrayOutputStream()
            assertTrue(protokoll.senden("peer", eingang {
                writeByte(3)
                writeUTF(id)
                writeUTF(ziel)
            }, DataOutputStream(ausgang), { true }, {}))
            assertEquals(ziel == "Freigegeben", ausgang.toByteArray().last() == 1.toByte())
        }
    }

    @Test fun verschiebenVerlangtBerechtigungenInQuelleUndZiel() {
        val doc = FamilienBerechtigungen.Dokument(id, "eigentuemer", "Quelle")
        val rechte = setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.VERSCHIEBEN)
        fun regel(kategorie: String, automatisch: Boolean = true) =
            FamilienBerechtigungen.Freigabe("peer", FamilienBerechtigungen.Bereich.KATEGORIE,
                kategorie, rechte, neueDokumenteAutomatisch = automatisch, archivEigentuemerId = doc.eigentuemerId)
        val quelle = regel("Quelle")
        val ziel = regel("Freigegeben")
        assertTrue(FamilienArchivRegeln.darfVerschieben("peer", doc, "Freigegeben", listOf(quelle, ziel)))
        assertFalse(FamilienArchivRegeln.darfVerschieben("peer", doc, "Freigegeben", listOf(quelle)))
        assertFalse(FamilienArchivRegeln.darfVerschieben("peer", doc, "Freigegeben", listOf(ziel)))
        assertFalse(FamilienArchivRegeln.darfVerschieben("peer", doc, "Freigegeben", listOf(quelle, regel("Freigegeben", false))))
        assertFalse(FamilienArchivRegeln.darfVerschieben("peer", doc, "Freigegeben", listOf(
            quelle, ziel.copy(bereich = FamilienBerechtigungen.Bereich.DOKUMENT, bereichId = doc.id))))
    }

    @Test fun verschiebenSendetZielUndMeldetAblehnungNachWiderruf() {
        var gespeichert: Boolean? = null
        val ziel = "Freigegeben"
        val protokoll = FamilienArchivAustausch(
            { emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _, _ ->
                val angebot = angebote.single()
                FamilienArchivAustausch.Anfrage(angebot.id, zielKategorie = angebot.zielKategorien.single())
            },
            { _, _, _ -> error("Verschieben darf keine PDF laden") },
            aenderungGemeldet = { gespeichert = it })
        val ausgang = ByteArrayOutputStream()
        assertTrue(protokoll.empfangen("peer", eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(false)
            writeBoolean(false)
            writeBoolean(false)
            writeByte(1)
            writeUTF(ziel)
            writeBoolean(false)
            writeByte(0)
            writeBoolean(false)
        }, DataOutputStream(ausgang), { true }, {}))
        assertEquals(false, gespeichert)
        val anfrage = DataInputStream(ByteArrayInputStream(ausgang.toByteArray()))
        assertEquals(3, anfrage.readUnsignedByte())
        assertEquals(id, anfrage.readUTF())
        assertEquals(ziel, anfrage.readUTF())
    }

    @Test fun loeschenErfordertAngebotUndBestaetigungVomEigentuemer() {
        for (angeboten in listOf(false, true)) {
            for (bestaetigt in listOf(false, true)) {
                var anfragen = 0
                val protokoll = FamilienArchivAustausch(
                    { listOf(eintrag.copy(loeschbar = angeboten)) },
                    { _, _, _ -> error("Löschen darf keine PDF laden") },
                    { _, _ -> false }, { _, _, _, _ -> null }, { _, _, _ -> },
                    loeschen = { peer, dokument ->
                        assertEquals("peer", peer)
                        assertEquals(id, dokument.id)
                        anfragen++
                        bestaetigt
                    })
                val ausgang = ByteArrayOutputStream()
                assertTrue(protokoll.senden("peer", eingang {
                    writeByte(4)
                    writeUTF(id)
                }, DataOutputStream(ausgang), { true }, {}))
                assertEquals(if (angeboten) 1 else 0, anfragen)
                assertEquals(angeboten && bestaetigt, ausgang.toByteArray().last() == 1.toByte())
            }
        }
    }

    @Test fun loeschAnfrageSendetNurKennungUndMeldetAblehnung() {
        var geloescht: Boolean? = null
        val protokoll = FamilienArchivAustausch(
            { emptyList() }, { _, _, _ -> false }, { _, _ -> false },
            { _, angebote, _, _ -> FamilienArchivAustausch.Anfrage(angebote.single().id, loeschen = true) },
            { _, _, _ -> error("Löschen darf keine PDF laden") },
            aenderungGemeldet = { geloescht = it })
        val ausgang = ByteArrayOutputStream()
        assertTrue(protokoll.empfangen("peer", eingang {
            writeInt(1)
            writeUTF(id)
            writeUTF(eintrag.titel)
            writeBoolean(false)
            writeBoolean(false)
            writeBoolean(false)
            writeByte(0)
            writeBoolean(true)
            writeByte(0)
            writeBoolean(false)
        }, DataOutputStream(ausgang), { true }, {}))
        assertEquals(false, geloescht)
        val anfrage = DataInputStream(ByteArrayInputStream(ausgang.toByteArray()))
        assertEquals(4, anfrage.readUnsignedByte())
        assertEquals(id, anfrage.readUTF())
        assertEquals(-1, anfrage.read())
    }

    @Test fun einreichungPrueftFreigabeAuchNachDemEmpfang() {
        val ziel = "Synthetische Kategorie"
        for (erlaubtePruefungen in 0..2) {
            var pruefungen = 0
            var empfangenePdf: ByteArray? = null
            val protokoll = FamilienArchivAustausch(
                { emptyList() }, { _, _, _ -> false }, { _, _ -> false },
                { _, _, _, _ -> null }, { _, _, _ -> },
                einreichZiele = { listOf(ziel) },
                darfEinreichen = { peer, kategorie ->
                    assertEquals("peer", peer)
                    assertEquals(ziel, kategorie)
                    ++pruefungen <= erlaubtePruefungen
                },
                pdfEinreichen = { _, _, titel, bytes ->
                    assertEquals(eintrag.titel, titel)
                    assertArrayEquals(pdf, bytes)
                    empfangenePdf = bytes
                    true
                })
            val bytes = ByteArrayOutputStream()
            assertTrue(protokoll.senden("peer", eingang {
                writeByte(5)
                writeUTF(eintrag.titel)
                writeUTF(ziel)
                if (erlaubtePruefungen > 0) {
                    writeInt(pdf.size)
                    write(pdf)
                }
            }, DataOutputStream(bytes), { true }, {}))
            assertEquals(erlaubtePruefungen == 2, bytes.toByteArray().last() == 1.toByte())
            if (erlaubtePruefungen == 2) assertArrayEquals(ByteArray(pdf.size), empfangenePdf)
            else assertNull(empfangenePdf)
        }
    }

    @Test fun einreichungFunktioniertOhneLesefreigabe() {
        val ziel = "Nur Einreichung"
        val regeln = listOf(FamilienBerechtigungen.Freigabe(
            "peer", FamilienBerechtigungen.Bereich.KATEGORIE, ziel,
            setOf(FamilienBerechtigungen.Aktion.IMPORTIEREN), archivEigentuemerId = "owner"))
        assertTrue(FamilienArchivRegeln.validieren("owner", "peer", regeln.single(), emptyList()))
        assertTrue(FamilienArchivRegeln.darfEinreichen("owner", "peer", ziel, regeln))
        assertFalse(FamilienArchivRegeln.darfEinreichen("owner", "peer", "Privat", regeln))
        assertFalse(FamilienArchivRegeln.darfEinreichen("owner", "fremd", ziel, regeln))
        val doc = FamilienBerechtigungen.Dokument(id, "owner", ziel)
        assertFalse(FamilienBerechtigungen.erlaubt("peer", doc, FamilienBerechtigungen.Aktion.ANSEHEN, regeln))
    }

    @Test fun einreichungSendetPdfErstNachZustimmung() {
        val ziel = "Synthetische Kategorie"
        for (angenommen in listOf(false, true)) {
            val eigeneBytes = pdf.copyOf()
            val protokoll = FamilienArchivAustausch(
                { emptyList() }, { _, _, _ -> false }, { _, _ -> false },
                { _, katalog, ziele, _ ->
                    assertTrue(katalog.isEmpty())
                    FamilienArchivAustausch.Anfrage(id, einreichKategorie = ziele.single())
                },
                { _, _, _ -> error("Einreichen darf keine Partner-PDF öffnen") },
                lokalePdf = { FamilienArchivAustausch.LokalePdf(eintrag.titel, eigeneBytes) })
            val bytes = ByteArrayOutputStream()
            assertTrue(protokoll.empfangen("peer", eingang {
                writeInt(0)
                writeByte(1)
                writeUTF(ziel)
                writeBoolean(angenommen)
                if (angenommen) writeBoolean(true)
            }, DataOutputStream(bytes), { true }, {}))
            val ausgabe = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals(5, ausgabe.readUnsignedByte())
            assertEquals(eintrag.titel, ausgabe.readUTF())
            assertEquals(ziel, ausgabe.readUTF())
            if (angenommen) {
                val daten = ByteArray(ausgabe.readInt())
                ausgabe.readFully(daten)
                assertArrayEquals(pdf, daten)
            }
            assertEquals(-1, ausgabe.read())
            assertArrayEquals(ByteArray(pdf.size), eigeneBytes)
        }
    }

    @Test fun einreichKennungBleibtBeiWiederholungGleich() {
        val id = FamilienArchivAustausch.einreichId("familiengeraet", "Kategorie", pdf)
        assertTrue(FamilienArchivAustausch.gueltig(id))
        assertEquals(id, FamilienArchivAustausch.einreichId("familiengeraet", "Kategorie", pdf.copyOf()))
        assertNotEquals(id, FamilienArchivAustausch.einreichId("anderes Geraet", "Kategorie", pdf))
        assertNotEquals(id, FamilienArchivAustausch.einreichId("familiengeraet", "Andere Kategorie", pdf))
        assertNotEquals(id, FamilienArchivAustausch.einreichId("familiengeraet", "Kategorie", pdf + 0.toByte()))
    }

    private fun eingang(schreiben: DataOutputStream.() -> Unit): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.schreiben() }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
}
