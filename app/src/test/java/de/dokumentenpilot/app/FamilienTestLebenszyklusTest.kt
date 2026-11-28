package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestLebenszyklusTest {
    @Test fun abbruch() {
        val scope = FamilienTestLebenszyklus()
        val geschlossen = mutableListOf<Int>()
        try {
            scope.registrieren(AutoCloseable { geschlossen.add(1) })
            scope.registrieren(AutoCloseable { geschlossen.add(2); error("Test") })
            assertTrue(scope.aktiv())
            scope.close()
            scope.close()
            assertFalse(scope.aktiv())
            assertEquals(listOf(2, 1), geschlossen)
            try {
                scope.registrieren(AutoCloseable { geschlossen.add(3) })
                fail("Late resource accepted")
            } catch (_: IllegalStateException) { }
            assertEquals(listOf(2, 1, 3), geschlossen)
        } finally { scope.close() }
    }
}
