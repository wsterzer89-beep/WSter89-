package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestEinladungFormatTest {
    @Test fun format() {
        val e = FamilienTestEinladungFormat.Einladung("192.168.1.10", 8443, "A".repeat(43), "a".repeat(64))
        val text = FamilienTestEinladungFormat.kodieren(e)
        assertEquals(e, FamilienTestEinladungFormat.lesen(text))
        for (falsch in listOf(text.replace("DPF1", "DPF2"), text.replace("192.168.1.10", "8.8.8.8"),
            text.replace("192.168.1.10", "localhost"), text.replace("8443", "0"), text + "|extra",
            text.replace("a".repeat(64), "x".repeat(64)), "x".repeat(161))) {
            assertNull(FamilienTestEinladungFormat.lesen(falsch))
        }
    }
}
