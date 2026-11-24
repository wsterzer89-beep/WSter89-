package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class JahresFilterTest {
    @Test fun explicitYearFiltersInvoices() {
        val request = UnterlagenErkennung.anforderungenErkennen("Rechnungen aus 2025").single()
        assertEquals(2025, request.jahr)
        val docs = listOf("a" to "Rechnung 2025-03.pdf", "b" to "Rechnung 2024-03.pdf", "c" to "Rechnung.pdf")
        assertEquals(listOf("a"), AssistentSuche.unterlagenZusammenstellen(docs, emptyMap(), listOf(request)).map { it.first })
    }
}
