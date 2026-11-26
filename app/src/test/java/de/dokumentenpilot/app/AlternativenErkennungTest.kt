package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class AlternativenErkennungTest {
    @Test
    fun alternativenInNeunSprachen() {
        listOf("oder", "or", "ou", "o", "lub", "или", "veya", "або", "أو").forEach { wort ->
            val punkte = UnterlagenErkennung.listenpunkteErkennen(
                "• Geburtsurkunde $wort Personalausweis\n• Energieausweis"
            )
            assertEquals(2, punkte.size)
            assertTrue(wort, punkte[0].alternative)
            assertEquals(setOf("Geburtsurkunde", "Personalausweis"),
                punkte[0].anforderungen.map { it.dokumentenart }.toSet())
            assertFalse(wort, punkte[1].alternative)
        }
    }

    @Test
    fun mehrzeiligeAlternative() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(
            "• Gehaltsabrechnungen\n  alternativ Lohnsteuerbescheinigung\n• Energieausweis"
        )
        assertEquals(2, punkte.size)
        assertTrue(punkte[0].alternative)
        assertEquals(setOf("Gehaltsabrechnungen", "Lohnsteuerbescheinigung"),
            punkte[0].anforderungen.map { it.dokumentenart }.toSet())
        assertFalse(punkte[1].alternative)
    }
}
