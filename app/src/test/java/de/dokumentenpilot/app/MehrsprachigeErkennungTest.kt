package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MehrsprachigeErkennungTest {
    @Test
    fun geburtsurkundenInNeunSprachen() {
        listOf("Geburtsurkunde", "birth certificate", "acte de naissance",
            "certificado de nacimiento", "akt urodzenia", "свидетельство о рождении",
            "doğum belgesi", "свідоцтво про народження", "شهادة الميلاد").forEach {
            assertTrue(it, SynonymKatalog.passt("Geburtsurkunde", it))
        }
    }

    @Test
    fun bedingungenInNeunSprachen() {
        listOf("gegebenenfalls", "if required", "si nécessaire", "si es necesario",
            "w razie potrzeby", "при необходимости", "gerektiğinde",
            "за потреби", "عند الحاجة").forEach {
            val punkte = UnterlagenErkennung.listenpunkteErkennen(
                "• $it Geburtsurkunde\n• Energieausweis"
            )
            assertEquals(2, punkte.size)
            assertTrue(it, punkte.first().bedingt)
        }
    }
}
