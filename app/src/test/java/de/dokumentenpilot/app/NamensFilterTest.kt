package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class NamensFilterTest {
    @Test fun explicitNameFilter() {
        val request = UnterlagenErkennung.anforderungenErkennen("Mietvertrag Person: \"Mia Muster\"").single()
        assertEquals("mia muster", request.namensFilter)
        val docs = listOf("a" to "Mietvertrag.pdf", "b" to "Mietvertrag.pdf", "c" to "Mietvertrag.pdf")
        val ocr = mapOf("a" to "Mietvertrag Mia Muster", "b" to "Mietvertrag Mia Musterfrau", "c" to "Mietvertrag")
        assertEquals(listOf("a"), AssistentSuche.unterlagenZusammenstellen(docs, ocr, listOf(request)).map { it.first })
    }
}
