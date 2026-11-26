package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class KaufvertragErkennungTest {
    @Test
    fun entwurfIstKeinEndgueltigerVertrag() {
        val text = "Entwurf des notariellen Kaufvertrages"
        assertTrue(SynonymKatalog.passt("Kaufvertragsentwurf", text))
        assertFalse(SynonymKatalog.passt("Notarieller Kaufvertrag", text))
        assertFalse("Notarieller Kaufvertrag" in SynonymKatalog.erkannteArten(text))
    }

    @Test
    fun getrennteAnforderungenBleibenErhalten() {
        val text = "Entwurf des notariellen Kaufvertrages; Notarieller Kaufvertrag"
        assertTrue(SynonymKatalog.passt("Kaufvertragsentwurf", text))
        assertTrue(SynonymKatalog.passt("Notarieller Kaufvertrag", text))
    }
}
