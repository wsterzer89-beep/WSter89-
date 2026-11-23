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

    @Test
    fun letzteDreiKontoauszuegeNachAuszugsmonat() {
        val kontoauszuege = listOf(
            "konto-juli" to "Kontoauszug Juli 2026",
            "konto-august" to "Kontoauszug August 2026",
            "konto-september" to "Kontoauszug September 2026",
            "konto-oktober" to "Kontoauszug Oktober 2026"
        )

        val ocr = mapOf(
            "konto-juli" to "Auszugszeitraum: 01.07.2026 - 31.07.2026",
            "konto-august" to "Datum: 01.09.2026\nAuszugszeitraum: 01.08.2026 - 31.08.2026",
            "konto-september" to "Auszugszeitraum: 01.09.2026 - 30.09.2026",
            "konto-oktober" to "Auszugszeitraum: 01.10.2026 - 31.10.2026"
        )

        val ergebnis = AssistentSuche.unterlagenZusammenstellen(
            kontoauszuege,
            ocr,
            listOf(UnterlagenAnforderung("Kontoauszüge", 3, true))
        )

        assertEquals(
            listOf("konto-oktober", "konto-september", "konto-august"),
            ergebnis.map { it.first }
        )
    }
}
