package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class ChecklistenZuordnungTest {
    @Test fun entfernenNurFuerEineAnforderung() {
        val mappe = UnterlagenMappe("test", "Test", mutableListOf("a", "b"))
        mappe.nachweiseZuordnen("A", listOf("a", "b"))
        mappe.nachweiseZuordnen("B", listOf("a"))
        mappe.nachweisEntfernen("A", "a")
        assertFalse(mappe.nachweisErlaubt("A", "a"))
        assertTrue(mappe.nachweisErlaubt("B", "a"))
        assertEquals(listOf("b"), mappe.manuelleNachweise["A"])
        assertEquals(listOf("a", "b"), mappe.dokumentIds)
        mappe.nachweiseZuordnen("A", listOf("a", "b"))
        assertTrue(mappe.nachweisErlaubt("A", "a"))
    }

    @Test fun automatischerTrefferKannEntferntWerden() {
        val mappe = UnterlagenMappe("test", "Test", mutableListOf("a"))
        mappe.nachweisEntfernen("A", "a")
        mappe.nachweisEntfernen("A", "a")
        assertEquals(listOf("a"), mappe.ausgeschlosseneNachweise["A"])
        assertFalse(mappe.nachweisErlaubt("A", "a"))
    }
}
