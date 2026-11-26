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

    @Test
    fun steuererklaerungAusArchivWirdGefundenUndErfuelltCheckliste() {
        val vorlage = "vorlage" to "Finanzierungsanforderungen"
        val steuer = "steuer" to "Steuererklärung.pdf"
        val texte = mapOf("vorlage" to "Benötigte Unterlagen: Steuererklärung",
            "steuer" to "Bitte reichen Sie folgende Unterlagen ein.")
        val anforderung = UnterlagenAnforderung("Steuererklärung", 1, false)
        assertEquals(listOf(steuer), AssistentSuche.fehlendeUnterlagen(
            listOf(vorlage), listOf(steuer), texte, anforderung))
        assertEquals(listOf(steuer), AssistentSuche.unterlagenZusammenstellen(
            listOf(vorlage, steuer, steuer), texte, listOf(anforderung)))
    }

    @Test
    fun falschesSteuerjahrErfuelltAnforderungNicht() {
        val steuer = "steuer" to "Steuererklärung 2023.pdf"
        assertTrue(AssistentSuche.unterlagenZusammenstellen(
            listOf(steuer), emptyMap(),
            listOf(UnterlagenAnforderung("Steuererklärung", 1, false, jahr = 2024))).isEmpty())
    }
}
