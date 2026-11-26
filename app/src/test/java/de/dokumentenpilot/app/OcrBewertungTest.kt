package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrBewertungTest {
    @Test fun passendeSchrift() {
        assertEquals(4, OcrBewertung.bewertung("كتاب ABC 123", "ara", 100))
        assertEquals(4, OcrBewertung.bewertung("тест ABC", "rus", 100))
        assertEquals(4, OcrBewertung.bewertung("мова ABC", "ukr", 100))
    }
    @Test fun unsichereOderFalscheSchrift() {
        assertEquals(0, OcrBewertung.bewertung("كتاب", "ara", 49))
        assertEquals(0, OcrBewertung.bewertung("test", "rus", 100))
        assertEquals(2, OcrBewertung.bewertung("тест", "rus", 50))
        assertEquals(0, OcrBewertung.bewertung("тест", "unknown", 100))
    }
}
