package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MappenImportRegelnTest {
    @Test
    fun nurPrivateUnreferenzierteDateienWerdenAusgewaehlt() {
        val ids = listOf("privat", "original", "geteilt", "ocr", "fehlt", "privat")
        val ergebnis = MappenImportRegeln.loeschbareIds(
            ids, setOf("geteilt"), setOf("ocr"),
            setOf("privat", "original", "geteilt", "ocr")
        ) { if (it == "original") "Allgemein" else MappenImportRegeln.KATEGORIE }
        assertEquals(listOf("privat"), ergebnis)
    }

    @Test
    fun keineAuswahlOhnePrivateKategorie() {
        assertEquals(emptyList<String>(), MappenImportRegeln.loeschbareIds(
            listOf("a"), emptySet(), emptySet(), setOf("a")
        ) { "Allgemein" })
    }
}
