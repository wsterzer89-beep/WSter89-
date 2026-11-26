package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestablaufTest {
    @Test
    fun einladungFreigabeWiderruf() {
        val demo = FamilienBerechtigungen.Dokument("demo-1", "owner", "Demo")
        val privat = FamilienBerechtigungen.Dokument("demo-2", "owner", "Privat")
        val fremd = FamilienBerechtigungen.Dokument("demo-1", "other-owner", "Demo")
        val dokumente = listOf(demo, privat, fremd)
        val einladung = FamilienEinladung.Einladung("demo", "owner", "member", 2000)
        assertFalse(FamilienBerechtigungen.PRODUKTIV_AKTIV)
        assertTrue(FamilienBerechtigungen.sichtbareDokumente("member", dokumente, emptyList()).isEmpty())
        assertNull(FamilienEinladung.annehmen(einladung, "other", 1000))
        assertNull(FamilienEinladung.annehmen(einladung, "member", 2000))
        val angenommen = FamilienEinladung.annehmen(einladung, "member", 1000)!!
        assertNull(FamilienEinladung.annehmen(angenommen, "member", 1001))
        val regel = FamilienBerechtigungen.Freigabe("member", FamilienBerechtigungen.Bereich.DOKUMENT,
            demo.id, FamilienBerechtigungen.vorlage(FamilienBerechtigungen.Vorlage.NUR_ANSEHEN),
            archivEigentuemerId = "owner")
        assertEquals(listOf(demo), FamilienBerechtigungen.sichtbareDokumente("member", dokumente, listOf(regel)))
        for (aktion in listOf(FamilienBerechtigungen.Aktion.WEITERGEBEN, FamilienBerechtigungen.Aktion.EINLADEN,
            FamilienBerechtigungen.Aktion.EXPORTIEREN, FamilienBerechtigungen.Aktion.LOESCHEN)) {
            assertFalse(FamilienBerechtigungen.erlaubt("member", demo, aktion, listOf(regel)))
        }
        assertNull(FamilienEinladung.widerrufen(angenommen, "member"))
        assertEquals(FamilienEinladung.Status.WIDERRUFEN, FamilienEinladung.widerrufen(angenommen, "owner")?.status)
        assertTrue(FamilienBerechtigungen.sichtbareDokumente("member", dokumente, listOf(regel.copy(widerrufen = true))).isEmpty())
    }
}
