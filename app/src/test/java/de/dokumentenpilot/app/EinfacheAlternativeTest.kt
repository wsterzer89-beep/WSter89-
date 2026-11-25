package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class EinfacheAlternativeTest {
    private fun punkt(text: String) =
        UnterlagenErkennung.listenpunkteErkennen("• $text\n• Energieausweis").first()

    @Test
    fun eindeutigeAlternative() {
        assertTrue(UnterlagenErkennung.einfacheAlternative(
            punkt("Geburtsurkunde oder Personalausweis")))
    }

    @Test
    fun komplexeAlternativenBleibenManuell() {
        listOf(
            "Geburtsurkunde oder Personalausweis und Energieausweis",
            "Geburtsurkunde oder Personalausweis und unbekannter Sondernachweis",
            "ggf. Geburtsurkunde oder Personalausweis",
            "Geburtsurkunde oder Personalausweis (folgt später)",
            "Geburtsurkunde oder Personalausweis oder Energieausweis"
        ).forEach {
            assertFalse(it, UnterlagenErkennung.einfacheAlternative(punkt(it)))
        }
    }
}
