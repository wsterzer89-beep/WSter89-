package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class UnterlagenErkennungTest {

    @Test
    fun letzteDreiEntgeltnachweise() {
        val ergebnis = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie die letzten 3 Entgeltnachweise ein."
        )
        val anforderung = ergebnis.first {
            it.dokumentenart == "Gehaltsabrechnungen"
        }
        assertEquals(3, anforderung.anzahl)
        assertTrue(anforderung.neuesteZuerst)
    }

    @Test
    fun sechsKontoauszuege() {
        val ergebnis = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie 6 Kontoauszüge ein."
        )
        val anforderung = ergebnis.first {
            it.dokumentenart == "Kontoauszüge"
        }
        assertEquals(6, anforderung.anzahl)
        assertFalse(anforderung.neuesteZuerst)
    }

    @Test
    fun zweiUnterschiedlicheMengen() {
        val ergebnis = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie 3 Gehaltsabrechnungen und 6 Kontoauszüge ein."
        )
        assertEquals(
            3,
            ergebnis.first { it.dokumentenart == "Gehaltsabrechnungen" }.anzahl
        )
        assertEquals(
            6,
            ergebnis.first { it.dokumentenart == "Kontoauszüge" }.anzahl
        )
    }
}
