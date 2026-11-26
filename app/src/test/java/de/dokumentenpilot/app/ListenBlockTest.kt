package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class ListenBlockTest {
    @Test
    fun eingerueckteFortsetzungNachLeerzeile() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(
            "• Gehaltsabrechnungen\n\n  (3 Monate)\n• Energieausweis"
        )
        assertEquals(2, punkte.size)
        assertEquals(3, punkte[0].anforderungen.first().anzahl)
    }

    @Test
    fun unverbundenerAbsatzBleibtAusserhalb() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(
            "• Geburtsurkunde\n\nAllgemeine Hinweise\n• Energieausweis"
        )
        assertEquals("Geburtsurkunde", punkte[0].original)
    }
}
