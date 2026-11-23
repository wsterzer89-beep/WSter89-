package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class UnterlagenSucheTest {

    @Test
    fun rechnungMitBruttoUndNettoIstKeineGehaltsabrechnung() {
        assertFalse(UnterlagenSuche.istGehaltsabrechnung(
            "Rechnung 2026-03.pdf", "Rechnung\nBrutto: 119,00 EUR\nNetto: 100,00 EUR"
        ))
    }

    @Test
    fun echteAbrechnungOhneTitelErkennen() {
        val text = """
            Arbeitgeber: Musterfirma GmbH
            Abrechnungsmonat: März 2026
            Gesamtbrutto: 3.500,00 EUR
            Nettoverdienst: 2.300,00 EUR
        """.trimIndent()

        assertTrue(
            UnterlagenSuche.istGehaltsabrechnung(
                "Dokument_123.pdf",
                text
            )
        )
    }

    @Test
    fun anforderungsschreibenNichtAlsAbrechnungErkennen() {
        val text = """
            Sehr geehrte Damen und Herren,
            bitte reichen Sie Ihre letzten drei
            Gehaltsabrechnungen und Kontoauszüge ein.
            Mit freundlichen Grüßen
            Ihre Bank
        """.trimIndent()

        assertFalse(
            UnterlagenSuche.istGehaltsabrechnung(
                "Bankanschreiben.pdf",
                text
            )
        )
    }

    @Test
    fun echteAbrechnungMitHinweisNichtAusschliessen() {
        val text = """
            Arbeitgeber: Musterfirma GmbH
            Abrechnungsmonat: März 2026
            Gesamtbrutto: 3.500,00 EUR
            Nettoverdienst: 2.300,00 EUR
            Hinweis: Bitte reichen Sie Rückfragen schriftlich ein.
        """.trimIndent()

        assertTrue(
            UnterlagenSuche.istGehaltsabrechnung(
                "Dokument_456.pdf",
                text
            )
        )
    }

    @Test
    fun bankanforderungMitAbrechnungsmerkmalenNichtErkennen() {
        val text = """
            Sehr geehrte Damen und Herren,
            bitte reichen Sie Ihre Gehaltsabrechnungen ein.
            Diese müssen Gesamtbrutto, Nettoverdienst
            und Abrechnungsmonat enthalten.
            Mit freundlichen Grüßen
            Ihre Bank
        """.trimIndent()

        assertFalse(
            UnterlagenSuche.istGehaltsabrechnung(
                "Bankanschreiben.pdf",
                text
            )
        )
    }
}
