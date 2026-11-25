package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class ListenErkennungTest {
    @Test
    fun mehrzeiligeUndUnbekanntePunkteBleibenErhalten() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(
            "• Gehaltsnachweise der letzten\n  3 Monate\n• Sonderformular XYZ"
        )
        assertEquals(2, punkte.size)
        assertEquals(3, punkte[0].anforderungen.single().anzahl)
        assertTrue(punkte[1].anforderungen.isEmpty())
        assertEquals("Sonderformular XYZ", punkte[1].original)
    }

    @Test
    fun bedingteUndSpaeterePunkteWerdenMarkiert() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(
            "• ggf. Rentenbescheid\n• Baugenehmigung (folgt später)"
        )
        assertEquals(2, punkte.size)
        assertTrue(punkte[0].bedingt)
        assertTrue(punkte[1].spaeter)
        assertFalse(punkte[1].bedingt)
    }

    @Test
    fun freieSucheBleibtUnveraendert() {
        assertTrue(UnterlagenErkennung.listenpunkteErkennen("Rentenbescheid").isEmpty())
    }

    @Test
    fun kaufvertragsentwurfIstKeinEndgueltigerVertrag() {
        assertTrue(SynonymKatalog.passt("Kaufvertragsentwurf", "Entwurf notarieller Kaufvertrag"))
        assertFalse(SynonymKatalog.passt("Notarieller Kaufvertrag", "Entwurf notarieller Kaufvertrag"))
    }
}
