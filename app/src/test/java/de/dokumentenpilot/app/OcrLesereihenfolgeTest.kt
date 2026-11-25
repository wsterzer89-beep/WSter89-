package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class OcrLesereihenfolgeTest {
    private val l1 = OcrLesereihenfolge.Block("L1", 10, 30, 90, 50)
    private val l2 = OcrLesereihenfolge.Block("L2", 10, 60, 90, 80)
    private val r1 = OcrLesereihenfolge.Block("R1", 110, 30, 190, 50)
    private val r2 = OcrLesereihenfolge.Block("R2", 110, 60, 190, 80)

    @Test fun spaltenMitUeberschrift() {
        val titel = OcrLesereihenfolge.Block("Titel", 10, 0, 190, 20)
        assertEquals(listOf(titel, l1, l2, r1, r2),
            OcrLesereihenfolge.sortieren(listOf(r2, l2, titel, r1, l1), 200))
    }

    @Test fun ungueltigeGeometrie() {
        val defekt = OcrLesereihenfolge.Block("Defekt", 100, 30, 100, 50)
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2, r1, r2, defekt), 200))
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2, r1, r2), 0))
    }

    @Test fun einspaltigBleibtUnveraendert() {
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2), 200))
    }

    @Test fun querblockInSpaltenVerhindertUmsortierung() {
        val quer = OcrLesereihenfolge.Block("Quer", 10, 51, 190, 59)
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2, r1, r2, quer), 200))
    }
}
