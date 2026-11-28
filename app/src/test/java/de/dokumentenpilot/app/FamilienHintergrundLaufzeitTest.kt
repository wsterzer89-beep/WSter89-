package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienHintergrundLaufzeitTest {
    private var minuten = 0L
    private val laufzeit = FamilienHintergrundLaufzeit { minuten * 60_000 }

    @Test fun alleDauernBeginnenErstBeiAktivierung() {
        val erwartungen = listOf(0L, 30L, 60L, 240L, 720L, null)
        FamilienFreigabedauer.entries.zip(erwartungen).forEach { (dauer, erwartet) ->
            assertEquals(0L, laufzeit.rest(dauer.name))
            laufzeit.aktivieren(dauer.name, dauer)
            assertEquals(erwartet?.times(60_000), laufzeit.rest(dauer.name))
        }
        minuten = 800
        assertEquals(listOf(FamilienFreigabedauer.UNBEGRENZT.name), laufzeit.aktive { true })
        assertNull(laufzeit.rest(FamilienFreigabedauer.UNBEGRENZT.name))
    }

    @Test fun gekoppelteGeraeteHabenUnabhaengigeBeginnUndEndzeiten() {
        laufzeit.aktivieren("a", FamilienFreigabedauer.MINUTEN_30)
        minuten = 10
        laufzeit.aktivieren("b", FamilienFreigabedauer.STUNDE)
        laufzeit.aktivieren("c", FamilienFreigabedauer.UNBEGRENZT)
        minuten = 30
        assertEquals(listOf("b", "c"), laufzeit.aktive { true })
        assertEquals(40 * 60_000L, laufzeit.rest("b"))
        laufzeit.beenden("c")
        assertEquals(listOf("b"), laufzeit.aktive { true })
        laufzeit.alleBeenden()
        assertTrue(laufzeit.aktive { true }.isEmpty())
    }

    @Test fun aenderungNutztUrspruenglichenStartUndReaktiviertKeineAbgelaufeneFreigabe() {
        laufzeit.aktivieren("a", FamilienFreigabedauer.STUNDE)
        minuten = 35
        laufzeit.aendern("a", FamilienFreigabedauer.STUNDEN_4)
        assertEquals(205 * 60_000L, laufzeit.rest("a"))
        laufzeit.aendern("a", FamilienFreigabedauer.MINUTEN_30)
        assertEquals(0L, laufzeit.rest("a"))
        laufzeit.aendern("a", FamilienFreigabedauer.UNBEGRENZT)
        assertEquals(0L, laufzeit.rest("a"))
        laufzeit.aktivieren("a", FamilienFreigabedauer.MINUTEN_30)
        minuten = 65
        laufzeit.aendern("a", FamilienFreigabedauer.UNBEGRENZT)
        assertEquals(0L, laufzeit.rest("a"))
    }

    @Test fun kopplungswiderrufUndProzessneustartVerwerfenAktivierungen() {
        laufzeit.aktivieren("a", FamilienFreigabedauer.UNBEGRENZT)
        assertTrue(laufzeit.aktive { false }.isEmpty())
        assertEquals(0L, laufzeit.rest("a"))
        val nachNeustart = FamilienHintergrundLaufzeit { 0L }
        assertEquals(0L, nachNeustart.rest("a"))
    }
}
