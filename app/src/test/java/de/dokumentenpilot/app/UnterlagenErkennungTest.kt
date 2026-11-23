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

    @Test
    fun einkommensteuererklaerung2025() {
        val ergebnis = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie Ihre Einkommensteuererklärung für das Jahr 2025 ein."
        )
        val anforderung = ergebnis.first {
            it.dokumentenart == "Steuererklärung"
        }
        assertEquals("Jahreserkennung: erwartetes Jahr 2025, erkanntes Jahr ${anforderung.jahr}", 2025, anforderung.jahr)
    }

    @Test
    fun kontoauszuegeDerLetztenDreiMonate() {
        val ergebnis = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie Ihre Kontoauszüge der letzten drei Monate ein."
        )
        val anforderung = ergebnis.first {
            it.dokumentenart == "Kontoauszüge"
        }
        assertEquals(3, anforderung.anzahl)
        assertTrue(anforderung.neuesteZuerst)
    }
}
