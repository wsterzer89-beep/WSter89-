package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLeserichtungTest {
    @Test fun spaltenrichtungMitUeberschrift() {
        val b = listOf(
            OcrLesereihenfolge.Block("Titel", 0, 0, 100, 10),
            OcrLesereihenfolge.Block("L1", 0, 20, 40, 30),
            OcrLesereihenfolge.Block("R1", 60, 20, 100, 30),
            OcrLesereihenfolge.Block("L2", 0, 40, 40, 50),
            OcrLesereihenfolge.Block("R2", 60, 40, 100, 50)
        )
        assertEquals(listOf("Titel", "R1", "R2", "L1", "L2"),
            OcrLesereihenfolge.sortieren(b, 100, rechtsNachLinks = true)?.map { it.text })
        assertEquals(listOf("Titel", "L1", "L2", "R1", "R2"),
            OcrLesereihenfolge.sortieren(b, 100)?.map { it.text })
    }
}
