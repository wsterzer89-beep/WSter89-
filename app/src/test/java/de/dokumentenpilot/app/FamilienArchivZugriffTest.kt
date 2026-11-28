package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienArchivZugriffTest {
    @Test fun archivGrenzenUndWiderruf() {
        val doc = FamilienBerechtigungen.Dokument("synthetisch", "owner", "Demo")
        val regel = FamilienBerechtigungen.Freigabe("peer", FamilienBerechtigungen.Bereich.DOKUMENT,
            doc.id, setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN),
            archivEigentuemerId = "owner")
        var aktiv = true
        var privat = emptySet<String>()
        var regeln = listOf(regel)
        var lesungen = 0
        var widerrufBeimLesen = false
        val bytes = byteArrayOf(1, 2, 3)
        val zugriff = FamilienArchivZugriff("owner", { it == "peer" }, { aktiv },
            { listOf(doc, doc.copy(id = "fremd", eigentuemerId = "other")) },
            { regeln }, { privat }, {
                lesungen++
                if (widerrufBeimLesen) regeln = emptyList()
                bytes
            })
        assertEquals(listOf(doc), zugriff.sichtbareDokumente("peer"))
        assertFalse(zugriff.ansehen("unknown", doc.id) { fail() })
        assertFalse(zugriff.ansehen("peer", "fremd") { fail() })
        privat = setOf(doc.id)
        assertFalse(zugriff.ansehen("peer", doc.id) { fail() })
        assertEquals(0, lesungen)
        privat = emptySet()
        assertTrue(zugriff.ansehen("peer", doc.id) { assertArrayEquals(byteArrayOf(1,2,3), it) })
        assertTrue(bytes.all { it == 0.toByte() })
        bytes.fill(7)
        widerrufBeimLesen = true
        assertFalse(zugriff.ansehen("peer", doc.id) { fail() })
        assertTrue(bytes.all { it == 0.toByte() })
        aktiv = false
        assertTrue(zugriff.sichtbareDokumente("peer").isEmpty())
        assertEquals(2, lesungen)
    }
}
