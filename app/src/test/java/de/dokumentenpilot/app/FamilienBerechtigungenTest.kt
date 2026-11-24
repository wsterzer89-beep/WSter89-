package de.dokumentenpilot.app

import de.dokumentenpilot.app.FamilienBerechtigungen.*
import org.junit.Assert.*
import org.junit.Test

class FamilienBerechtigungenTest {
    private val privat = Dokument("privat", "besitzer", "Gesundheit")
    private val geteilt = Dokument("geteilt", "besitzer", "Arbeit", setOf("mappe-a"))
    private val neu = Dokument("neu", "besitzer", "Arbeit", setOf("mappe-a"))
    private val freigabe = Freigabe(
        "kind", Bereich.KATEGORIE, "Arbeit", vorlage(Vorlage.NUR_ANSEHEN),
        dokumentIdsBeiFreigabe = setOf("geteilt")
    )

    @Test fun standardmaessigPrivat() {
        assertFalse(PRODUKTIV_AKTIV)
        assertFalse(erlaubt("kind", privat, Aktion.ANSEHEN, listOf(freigabe)))
        assertFalse(erlaubt("kind", neu, Aktion.ANSEHEN, listOf(freigabe)))
        assertFalse(erlaubt("kind", geteilt, Aktion.LOESCHEN, listOf(freigabe)))
    }

    @Test fun sichtbarerBereichUndAktionMuessenZusammenpassen() {
        assertTrue(erlaubt("kind", geteilt, Aktion.ANSEHEN, listOf(freigabe)))
        assertEquals(listOf(geteilt), sichtbareDokumente("kind", listOf(privat, geteilt, neu), listOf(freigabe)))
        val nurLoeschen = Freigabe("kind", Bereich.DOKUMENT, "privat", setOf(Aktion.LOESCHEN))
        assertFalse(erlaubt("kind", geteilt, Aktion.LOESCHEN, listOf(freigabe, nurLoeschen)))
    }

    @Test fun entzugUndAutomatischeErweiterung() {
        assertFalse(erlaubt("kind", geteilt, Aktion.ANSEHEN, listOf(freigabe.copy(widerrufen = true))))
        assertTrue(erlaubt("kind", neu, Aktion.ANSEHEN,
            listOf(freigabe.copy(neueDokumenteAutomatisch = true))))
        assertFalse(erlaubt("fremd", geteilt, Aktion.ANSEHEN, listOf(freigabe)))
    }

    @Test fun administrativeRechteNichtPerMitarbeit() {
        assertFalse(Aktion.EINLADEN in vorlage(Vorlage.MITARBEIT))
        assertFalse(Aktion.WEITERGEBEN in vorlage(Vorlage.MITARBEIT))
        assertFalse(Aktion.LOESCHEN in vorlage(Vorlage.MITARBEIT))
    }
}
