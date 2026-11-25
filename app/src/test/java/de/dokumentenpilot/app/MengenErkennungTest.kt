package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MengenErkennungTest {
    @Test
    fun monatsmengenInNeunSprachen() {
        listOf("mind. 3 Monate", "3 months", "3 mois", "3 meses",
            "3 miesiące", "3 месяца", "3 ay", "3 місяці", "3 أشهر").forEach { menge ->
            val anforderung = UnterlagenErkennung.anforderungenErkennen(
                "Gehaltsabrechnungen ($menge)"
            ).first { it.dokumentenart == "Gehaltsabrechnungen" }
            assertEquals(menge, 3, anforderung.anzahl)
            assertTrue(menge, anforderung.neuesteZuerst)
        }
    }
}
