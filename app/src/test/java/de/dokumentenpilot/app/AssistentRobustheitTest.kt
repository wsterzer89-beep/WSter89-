package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class AssistentRobustheitTest {
    @Test fun blankSearchDoesNotMatchEverything() {
        assertFalse(AssistentSuche.ocrSuchePasst("Rechnung.pdf", "Rechnung", "   "))
    }

    @Test fun existingDocumentIsNotSuggestedAgain() {
        val doc = "a" to "Mietvertrag.pdf"
        val anforderung = UnterlagenAnforderung("Mietvertrag", 2, false)
        assertTrue(AssistentSuche.fehlendeUnterlagen(
            listOf(doc), listOf(doc), emptyMap(), anforderung
        ).isEmpty())
    }

    @Test fun negativeQuantityDoesNotCrash() {
        assertTrue(AssistentSuche.unterlagenZusammenstellen(
            listOf("a" to "Mietvertrag.pdf"), emptyMap(),
            listOf(UnterlagenAnforderung("Mietvertrag", -1, false))
        ).isEmpty())
    }
}
