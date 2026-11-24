package de.dokumentenpilot.app

import de.dokumentenpilot.app.FamilienBerechtigungen.Aktion
import de.dokumentenpilot.app.FamilienBerechtigungen.Bereich
import de.dokumentenpilot.app.FamilienBerechtigungen.Dokument
import de.dokumentenpilot.app.FamilienBerechtigungen.Freigabe
import de.dokumentenpilot.app.FamilienBerechtigungen.Vorlage
import de.dokumentenpilot.app.FamilienBerechtigungen.PRODUKTIV_AKTIV
import de.dokumentenpilot.app.FamilienBerechtigungen.erlaubt
import de.dokumentenpilot.app.FamilienBerechtigungen.sichtbareDokumente
import de.dokumentenpilot.app.FamilienBerechtigungen.vorlage
import org.junit.Assert.*
import org.junit.Test

class FamilienBerechtigungenTest {
    private val privat = Dokument("privat", "besitzer", "Gesundheit")
    private val geteilt = Dokument("geteilt", "besitzer", "Arbeit", setOf("mappe-a"))
    private val neu = Dokument("neu", "besitzer", "Arbeit", setOf("mappe-a"))
    private val freigabe = Freigabe(
        "kind", Bereich.KATEGORIE, "Arbeit", vorlage(Vorlage.NUR_ANSEHEN),
        dokumentIdsBeiFreigabe = setOf("geteilt"),
        archivEigentuemerId = "besitzer"
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
        val nurLoeschen = Freigabe("kind", Bereich.DOKUMENT, "privat", setOf(Aktion.LOESCHEN),
            archivEigentuemerId = "besitzer")
        assertFalse(erlaubt("kind", geteilt, Aktion.LOESCHEN, listOf(freigabe, nurLoeschen)))
    }

    @Test fun entzugUndAutomatischeErweiterung() {
        assertFalse(erlaubt("kind", geteilt, Aktion.ANSEHEN, listOf(freigabe.copy(widerrufen = true))))
        assertTrue(erlaubt("kind", neu, Aktion.ANSEHEN,
            listOf(freigabe.copy(neueDokumenteAutomatisch = true))))
        assertFalse(erlaubt("fremd", geteilt, Aktion.ANSEHEN, listOf(freigabe)))
    }

    @Test fun andereArchiveBleibenPrivatBeiAutomatischenFreigaben() {
        val fremdesDokument = Dokument("anderes", "andereFamilie", "Arbeit", setOf("mappe-a"))
        assertFalse(erlaubt("kind", fremdesDokument, Aktion.ANSEHEN,
            listOf(freigabe.copy(neueDokumenteAutomatisch = true))))
    }

    @Test fun administrativeRechteNichtPerMitarbeit() {
        assertFalse(Aktion.EINLADEN in vorlage(Vorlage.MITARBEIT))
        assertFalse(Aktion.WEITERGEBEN in vorlage(Vorlage.MITARBEIT))
        assertFalse(Aktion.LOESCHEN in vorlage(Vorlage.MITARBEIT))
    }
}
