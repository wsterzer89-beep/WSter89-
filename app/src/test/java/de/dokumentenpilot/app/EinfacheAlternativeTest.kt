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
    fun dreiAlternativen() {
        val p = punkt("Geburtsurkunde oder Personalausweis oder Energieausweis")
        assertTrue(UnterlagenErkennung.einfacheAlternative(p))
        assertTrue(UnterlagenErkennung.alternativeErfuellt(p) {
            if (it.dokumentenart == "Energieausweis") 1 else 0
        })
        assertFalse(UnterlagenErkennung.alternativeErfuellt(p) { 0 })
    }

    @Test
    fun komplexeAlternativenBleibenManuell() {
        listOf(
            "Geburtsurkunde oder Personalausweis und Energieausweis",
            "Geburtsurkunde oder Personalausweis und unbekannter Sondernachweis",
            "ggf. Geburtsurkunde oder Personalausweis",
            "Geburtsurkunde oder Personalausweis (folgt später)"
        ).forEach {
            assertFalse(it, UnterlagenErkennung.einfacheAlternative(punkt(it)))
        }
    }
}
