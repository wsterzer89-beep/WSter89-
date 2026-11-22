package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class AssistentSucheTest {

    private val dokumente = listOf(
        "ausweis-id" to "Personalausweis",
        "gehalt-1" to "Gehaltsabrechnung Januar 2026",
        "gehalt-2" to "Gehaltsabrechnung Februar 2026",
        "gehalt-3" to "Gehaltsabrechnung März 2026",
        "gehalt-4" to "Gehaltsabrechnung März 2026 Kopie"
    )

    @Test
    fun personalausweisOhneDatum() {
        val ergebnis = AssistentSuche.unterlagenZusammenstellen(
            dokumente,
            emptyMap(),
            listOf(UnterlagenAnforderung("Personalausweis", null, false))
        )

        assertEquals(listOf("ausweis-id"), ergebnis.map { it.first })
    }

    @Test
    fun personalausweisTrotzNeuesteZuerst() {
        val ergebnis = AssistentSuche.unterlagenZusammenstellen(
            dokumente,
            emptyMap(),
            listOf(UnterlagenAnforderung("Personalausweis", null, true))
        )

        assertEquals(listOf("ausweis-id"), ergebnis.map { it.first })
    }

    @Test
    fun letzteDreiGehaltsabrechnungenAusVerschiedenenMonaten() {
        val ergebnis = AssistentSuche.unterlagenZusammenstellen(
            dokumente,
            emptyMap(),
            listOf(UnterlagenAnforderung("Gehaltsabrechnungen", 3, true))
        )

        assertEquals(3, ergebnis.size)
        assertEquals(
            setOf("gehalt-1", "gehalt-2"),
            ergebnis.map { it.first }.toSet().minus(
                setOf("gehalt-3", "gehalt-4")
            )
        )
        assertTrue(
            ergebnis.any { it.first == "gehalt-3" || it.first == "gehalt-4" }
        )
    }
}
