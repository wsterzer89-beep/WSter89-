package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class MonatsZeitraumTest {
    @Test fun numericAcrossYears() {
        assertEquals(202411..202502, MonatsZeitraum.erkennen("von 11/2024 bis 02/2025"))
    }
    @Test fun namedMonthsSharedYear() {
        assertEquals(202503..202506, MonatsZeitraum.erkennen("März bis Juni 2025"))
    }
    @Test fun reversedIntervalIsEmpty() {
        assertTrue(MonatsZeitraum.erkennen("Juni bis März 2025")!!.isEmpty())
    }
    @Test fun requestFiltersOutsideAndUnknownDates() {
        val request = UnterlagenErkennung.anforderungenErkennen("Gehaltsabrechnungen von November 2024 bis Februar 2025").single()
        assertEquals(202411, request.monatVon)
        assertEquals(202502, request.monatBis)
        val docs = listOf("a" to "Gehaltsabrechnung 2024-10.pdf", "b" to "Gehaltsabrechnung 2024-11.pdf", "c" to "Gehaltsabrechnung 2025-02.pdf", "d" to "Gehaltsabrechnung 2025-03.pdf", "e" to "Gehaltsabrechnung.pdf")
        assertEquals(setOf("b", "c"), AssistentSuche.unterlagenZusammenstellen(docs, emptyMap(), listOf(request)).map { it.first }.toSet())
    }
}
