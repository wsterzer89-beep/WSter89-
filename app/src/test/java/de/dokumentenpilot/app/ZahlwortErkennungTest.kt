package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class ZahlwortErkennungTest {
    @Test
    fun monatsUndJahresmengen() {
        val beispiele = listOf(
            "drei Monate" to "drei Jahre",
            "three months" to "three years",
            "trois mois" to "trois ans",
            "tres meses" to "tres años",
            "trzy miesiące" to "trzy lata",
            "три месяца" to "три года",
            "üç ay" to "üç yıl",
            "три місяці" to "три роки",
            "ثلاثة أشهر" to "ثلاثة سنوات"
        )
        beispiele.forEach { (monat, jahr) ->
            val m = UnterlagenErkennung.anforderungenErkennen("Gehaltsabrechnungen ($monat)")
                .first { it.dokumentenart == "Gehaltsabrechnungen" }
            val j = UnterlagenErkennung.anforderungenErkennen("Jahresabschluss ($jahr)")
                .first { it.dokumentenart == "Jahresabschluss" }
            assertEquals(monat, 3, m.anzahl)
            assertEquals(jahr, 3, j.anzahl)
            assertTrue(m.neuesteZuerst)
            assertTrue(j.neuesteZuerst)
        }
    }
}
