package de.dokumentenpilot.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SynonymKatalogTest {
    @Test fun alternativeBezeichnungen() {
        val beispiele = mapOf(
            "Lohnnachweis" to "Gehaltsabrechnungen",
            "Entgeltbescheinigung" to "Gehaltsabrechnungen",
            "Bankauszüge" to "Kontoauszüge",
            "Anstellungsvertrag" to "Arbeitsvertrag",
            "Mietvereinbarung" to "Mietvertrag",
            "Darlehensvertrag" to "Kreditvertrag"
        )
        beispiele.forEach { (text, art) ->
            assertTrue(text, UnterlagenErkennung.passtZuUnterlage(art, text))
        }
    }

    @Test fun unterschiedlicheNachweiseNichtVerwechseln() {
        assertFalse(UnterlagenErkennung.passtZuUnterlage("Renteninformation", "Rentenbescheid"))
        assertTrue(UnterlagenErkennung.passtZuUnterlage("Rentenbescheid", "Rentenbescheid"))
        assertFalse(UnterlagenErkennung.passtZuUnterlage("Rechnung", "Zahlungsaufforderung"))
        assertFalse(UnterlagenErkennung.passtZuUnterlage("Kreditvertrag", "Baufinanzierung"))
    }
}
