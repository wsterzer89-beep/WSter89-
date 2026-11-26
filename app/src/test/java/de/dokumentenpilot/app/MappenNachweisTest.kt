package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class MappenNachweisTest {
    @Test
    fun suchvorlageIstKeinNachweis() {
        val dokumente = listOf("vorlage" to "Energieausweis", "echt" to "Geburtsurkunde")
        val texte = mapOf("vorlage" to "Energieausweis Geburtsurkunde")
        val nachweise = MappenImportRegeln.nachweise(dokumente) {
            if (it == "vorlage") MappenImportRegeln.KATEGORIE else "Allgemein"
        }
        val cache = mutableMapOf<String, Boolean>()
        assertTrue(AssistentSuche.unterlagenZusammenstellen(nachweise, texte,
            listOf(UnterlagenAnforderung("Energieausweis", 1, false)), cache).isEmpty())
        assertEquals(listOf("echt"), AssistentSuche.unterlagenZusammenstellen(nachweise, texte,
            listOf(UnterlagenAnforderung("Geburtsurkunde", 1, false)), cache).map { it.first })
        assertEquals(2, dokumente.size)
    }
}
