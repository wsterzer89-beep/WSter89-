package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test
import de.dokumentenpilot.app.FamilienArchivNavigation.Bereich
import de.dokumentenpilot.app.FamilienArchivNavigation.Seite

class FamilienArchivNavigationTest {
    private val a = FamilienArchivAustausch.Eintrag("a", "Ein synthetisches PDF", true, false,
        quellKategorie = "Versicherungen", mappen = listOf("Familie", "Nachweise", "Familie"))
    private val b = a.copy(id = "b", titel = "Ein anderes PDF", mappen = listOf("Nachweise"),
        kategorieSichtbar = false)

    @Test fun gruppenVerwendenNurFreigegebeneMetadatenUndEindeutigeDokumente() {
        val mappen = FamilienArchivNavigation.gruppieren(listOf(a, b, a), mappen = true)
        assertEquals(listOf(a), mappen["Familie"])
        assertEquals(listOf(a, b), mappen["Nachweise"])
        assertEquals(mapOf("Versicherungen" to listOf(a)),
            FamilienArchivNavigation.gruppieren(listOf(a, b, a), mappen = false))
    }

    @Test fun zurueckLaesstJeweiligeScrollpositionUnveraendert() {
        val nav = FamilienArchivNavigation()
        nav.oeffnen(Seite(Bereich.MAPPEN), 70)
        nav.oeffnen(Seite(Bereich.MAPPE, "Nachweise"), 102)
        nav.merken(150)
        assertEquals(150, nav.scrollposition)
        assertTrue(nav.zurueck())
        assertEquals(Seite(Bereich.MAPPEN), nav.seite)
        assertEquals(102, nav.scrollposition)
        assertTrue(nav.zurueck())
        assertEquals(Seite(Bereich.ARCHIV), nav.seite)
        assertEquals(70, nav.scrollposition)
        assertFalse(nav.zurueck())
    }

    @Test fun dokumentmenueZeigtNurUnterstuetzteAktionen() {
        assertEquals(listOf("Öffnen", "Dokumenteninformationen"), FamilienArchivNavigation.aktionen(a))
        assertEquals(listOf("Öffnen", "Umbenennen", "Verschieben", "Dokumenteninformationen", "Löschung anfragen"),
            FamilienArchivNavigation.aktionen(a.copy(umbenennbar = true, zielKategorien = listOf("Ziel"), loeschbar = true)))
    }

    @Test fun hintergrundKatalogRespektiertPrivateAusnahmenUndZeigtKeineBearbeitung() {
        val owner = "owner"
        val peer = "peer"
        val vorhandenes = FamilienBerechtigungen.Dokument("a", owner, "Geheime Kategorie", setOf("map"))
        val zukuenftig = vorhandenes.copy(id = "future")
        val mappe = FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.MAPPE, "map",
            setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.UMBENENNEN),
            setOf(vorhandenes.id), true, archivEigentuemerId = owner)
        val angeboten = FamilienArchivDaten.neuesAngebot(peer, listOf(
            FamilienArchivDaten.Eintrag(vorhandenes, "Privat", mapOf("map" to "Freigegebene Mappe")),
            FamilienArchivDaten.Eintrag(zukuenftig, "Neu", mapOf("map" to "Freigegebene Mappe", "privat" to "Geheim"))
        ), emptyList(), FamilienArchivFreigaben.Regeln(listOf(mappe), setOf(vorhandenes.id)), bearbeiten = false)
        val eintrag = angeboten.single()
        assertEquals("future", eintrag.id)
        assertEquals(listOf("Freigegebene Mappe"), eintrag.mappen)
        assertFalse(eintrag.kategorieSichtbar)
        assertFalse(eintrag.umbenennbar)
        assertFalse(eintrag.loeschbar)
    }

    @Test fun geraetenamenOhneTechnischeKennung() {
        assertEquals(listOf("Alex · Gerät 1", "Sam", "Alex · Gerät 2", "Familiengerät"),
            FamilienArchivNavigation.geraetenamen(listOf("Alex", "Sam", "Alex", null)))
    }
}
