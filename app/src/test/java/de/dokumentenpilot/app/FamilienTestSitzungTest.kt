package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestSitzungTest {
    @Test fun ablauf() {
        var zeit = 100L
        val s = FamilienTestSitzung { zeit }
        val token = s.einladungsToken()!!
        assertNull(s.testdaten())
        assertFalse(s.bestaetigen())
        assertFalse(s.annehmen(ByteArray(31)))
        assertTrue(s.annehmen(token))
        assertFalse(s.annehmen(token))
        assertNull(s.einladungsToken())
        assertNull(s.testdaten())
        assertTrue(s.bestaetigen())
        assertNotNull(s.testdaten())
        s.beenden()
        assertNull(s.testdaten())
        assertFalse(s.bestaetigen())
        assertFalse(s.annehmen(token))
        val neu = FamilienTestSitzung { zeit }
        val neuToken = neu.einladungsToken()!!
        zeit += 600_000
        assertFalse(neu.annehmen(neuToken))
        assertNull(neu.einladungsToken())
    }
    @Test fun paralleleAnnahme() {
        val s = FamilienTestSitzung { 0L }
        val token = s.einladungsToken()!!
        val erfolge = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(8) { Thread { if (s.annehmen(token)) erfolge.incrementAndGet() } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, erfolge.get())
    }
}
