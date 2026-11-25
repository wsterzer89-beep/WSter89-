package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class ArchivAnzeigeTest {
    @Test fun leereSucheZeigtAlle17Dokumente() {
        assertEquals(17, (1..17).count { ArchivAnzeige.passt(true, "Dokument $it.pdf", "", "") })
    }
    @Test fun neuesDokumentOhneOcrSichtbar() {
        assertTrue(ArchivAnzeige.passt(true, "Neu.pdf", "", "  "))
    }
    @Test fun andereKategorieBleibtAusgeblendet() {
        assertFalse(ArchivAnzeige.passt(false, "Neu.pdf", "", ""))
    }
    @Test fun namensUndOcrSucheBleibenErhalten() {
        assertTrue(ArchivAnzeige.passt(true, "Vertrag.pdf", "", "Vertrag"))
        assertTrue(ArchivAnzeige.passt(true, "Scan.pdf", "Zebra", "Zebra"))
        assertFalse(ArchivAnzeige.passt(true, "Scan.pdf", "", "Zebra"))
        assertFalse(ArchivAnzeige.passt(false, "Vertrag.pdf", "", "Vertrag"))
    }
}
