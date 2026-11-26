package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestLinkTest {
    @Test fun linkFormat() {
        val code = FamilienTestEinladungFormat.kodieren(
            FamilienTestEinladungFormat.Einladung("192.168.1.10", 8443, "A".repeat(43), "a".repeat(64)))
        val link = FamilienTestLink.erstellen(code)
        assertEquals(code, FamilienTestLink.lesen(link))
        listOf(link + "?extra=1", link + "#fragment", link + "/", link + "=",
            link.replace("dokumentenpilot:", "https:"), link.replace("familie/", "fremd/"),
            "dokumentenpilot://familie/einladung/" + "A".repeat(300)).forEach {
            assertNull(FamilienTestLink.lesen(it))
        }
    }
}
