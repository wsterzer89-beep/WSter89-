package de.dokumentenpilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrechnungAblaufTest {
    private val dokumente = listOf(
        "dez" to "Gehaltsabrechnung_Dezember_2025.pdf",
        "jan" to "Scan A.pdf",
        "feb" to "Scan B.pdf",
        "feb-kopie" to "Lohnabrechnung_02_2026.pdf",
        "mrz" to "Scan C.pdf",
        "brief" to "Lohnabrechnung April 2026.pdf",
        "undatiert" to "Gehaltsabrechnung.pdf"
    )
    private val ocr = mapOf(
        "jan" to "Lohnabrechnung\nfür Januar 2026",
        "feb" to "Auszahlungsdatum: 01.03.2026\nVerdienst-\nabrechnung Februar 2026",
        "mrz" to "Bezüge Abrechnung für 03/2026",
        "brief" to """
            Bitte
            reichen Sie Ihre Lohnabrechnung für April 2026 ein.
            Beispiel: Abrechnungsmonat April 2026, Gesamtbrutto: 3000,00 EUR.
        """.trimIndent()
    )

    @Test
    fun mengenanfrageFindetDieNeuestenDreiAbrechnungsmonate() {
        for (eingabe in listOf(
            "Zeig mir die letzten drei Gehaltsabrechnungen",
            "Die letzten 3 Verdienstabrechnungen",
            "Neueste Lohnabrechnungen",
            "Bezügeabrechnungen der letzten drei Monate"
        )) {
            val anfrage = requireNotNull(AssistentErkennung.erkennen(eingabe))
            assertEquals("Gehaltsabrechnungen", anfrage.dokumentTyp)
            assertEquals(3, anfrage.anzahl)

            val auswahl = AssistentSuche.assistentTreffer(dokumente, ocr, anfrage)
            assertEquals(listOf("mrz", "feb", "jan"), auswahl.map { it.first })
        }
    }

    @Test
    fun checklisteZaehltKopienUndUndatierteNichtAlsWeitereMonate() {
        val anforderung = UnterlagenErkennung.anforderungenErkennen(
            "Bitte reichen Sie 3 Lohnabrechnungen für das Jahr 2026 ein."
        ).single()
        val inhalt = dokumente.filter { it.first in setOf("dez", "feb", "feb-kopie", "brief", "undatiert") }
        val archiv = dokumente - inhalt.toSet()

        assertEquals(
            listOf("feb"),
            AssistentSuche.unterlagenZusammenstellen(inhalt, ocr, listOf(anforderung)).map { it.first }
        )
        assertEquals(
            listOf("mrz", "jan"),
            AssistentSuche.fehlendeUnterlagen(inhalt, archiv, ocr, anforderung).map { it.first }
        )
    }

    @Test
    fun anforderungMitBeispielbetraegenWirdAuchInDerOcrSucheAusgeschlossen() {
        assertFalse(AssistentSuche.ocrSuchePasst(
            "Gehaltsabrechnung April 2026.pdf",
            "Bitte reichen Sie Gehaltsabrechnungen ein.\nBrutto: 3000,00\nNetto: 2000,00",
            "Lohnabrechnung"
        ))
        assertFalse(UnterlagenSuche.istGehaltsabrechnung("Unterlagenanforderung_Lohnabrechnungen.pdf", ""))
        assertFalse(UnterlagenSuche.istGehaltsabrechnung("Brief.pdf", "Lohnabrechnungen der letzten drei Monate"))
        assertTrue(AssistentSuche.ocrSuchePasst("scan.pdf", ocr.getValue("feb"), "Gehaltsabrechnung"))
        assertTrue(AssistentSuche.ocrSuchePasst("scan.pdf", "Musterfirma\n  Berlin", "Musterfirma Berlin"))
    }

    @Test
    fun mengeWirdDerRichtigenChecklistenpositionZugeordnet() {
        val anforderungen = UnterlagenErkennung.anforderungenErkennen(
            "Gehaltsabrechnungen und Kontoauszüge der letzten sechs Monate"
        ).associateBy { it.dokumentenart }

        assertEquals(null, anforderungen.getValue("Gehaltsabrechnungen").anzahl)
        assertEquals(6, anforderungen.getValue("Kontoauszüge").anzahl)
    }

    @Test
    fun abrechnungszeitraumHatVorrangVorDateinameUndZahltag() {
        assertEquals(202512, AssistentDatum.abrechnungsmonat(
            "Lohnabrechnung_Januar_2026.pdf",
            "Zahltag: 02.01.2026\nAbrechnungszeitraum:\n01.12.2025 - 31.12.2025"
        ))
        assertEquals(null, AssistentDatum.abrechnungsmonat(
            "scan.pdf", "Lohnabrechnung\nAuszahlungsdatum: 02.01.2026"
        ))
    }
}
