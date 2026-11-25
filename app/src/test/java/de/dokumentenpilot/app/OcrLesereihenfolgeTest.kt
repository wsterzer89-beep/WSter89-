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

    @Test fun asymmetrischeSpalten() {
        val a = l1.copy(right = 130)
        val b = l2.copy(right = 130)
        val c = r1.copy(left = 145)
        val d = r2.copy(left = 145)
        assertEquals(listOf(a, b, c, d),
            OcrLesereihenfolge.sortieren(listOf(d, b, c, a), 200))
    }

    @Test fun asymmetrischeSpaltenMitTitel() {
        val titel = OcrLesereihenfolge.Block("Titel", 10, 0, 190, 20)
        val a = l1.copy(right = 130)
        val b = l2.copy(right = 130)
        val c = r1.copy(left = 145)
        val d = r2.copy(left = 145)
        assertEquals(listOf(titel, a, b, c, d),
            OcrLesereihenfolge.sortieren(listOf(d, titel, b, c, a), 200))
    }

    @Test fun versetzteBloeckeOhneVertikaleUeberlappung() {
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2,
            r1.copy(top = 100, bottom = 120),
            r2.copy(top = 130, bottom = 150)), 200))
    }

    @Test fun einspaltigBleibtUnveraendert() {
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2), 200))
    }

    @Test fun querblockInSpaltenVerhindertUmsortierung() {
        val quer = OcrLesereihenfolge.Block("Quer", 10, 51, 190, 59)
        assertNull(OcrLesereihenfolge.sortieren(listOf(l1, l2, r1, r2, quer), 200))
    }
}
