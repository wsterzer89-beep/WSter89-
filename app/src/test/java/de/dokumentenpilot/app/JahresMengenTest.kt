package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class JahresMengenTest {
    @Test
    fun jahresmengenInNeunSprachen() {
        listOf("3 Jahre", "3 years", "3 ans", "3 años", "3 lata",
            "3 года", "3 yıl", "3 роки", "3 سنوات").forEach { menge ->
            val a = UnterlagenErkennung.anforderungenErkennen("Jahresabschluss ($menge)")
                .first { it.dokumentenart == "Jahresabschluss" }
            assertEquals(menge, 3, a.anzahl)
            assertTrue(menge, a.neuesteZuerst)
        }
    }

    @Test
    fun gueltigkeitsdauerIstKeineAnzahl() {
        val a = UnterlagenErkennung.anforderungenErkennen("Energieausweis (10 Jahre)")
            .first { it.dokumentenart == "Energieausweis" }
        assertNull(a.anzahl)
    }
}
