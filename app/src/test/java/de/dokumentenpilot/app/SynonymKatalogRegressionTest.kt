package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class SynonymKatalogRegressionTest {
    @Test fun alleBegriffsfamilienErkennenEigeneBezeichnungen() {
        SynonymKatalog.familien.forEach { (art, begriffe) ->
            begriffe.forEach { begriff ->
                assertTrue("$art: $begriff", SynonymKatalog.passt(art, begriff))
            }
        }
    }

    @Test fun fremdeDokumenttypenNichtGleichsetzen() {
        val paare = listOf(
            "Rentenbescheid" to "Renteninformation",
            "Rentenbescheid" to "Rentenversicherungsverlauf",
            "Gehaltsabrechnungen" to "Lohnsteuerbescheinigung",
            "Rechnung" to "Zahlungsaufforderung",
            "Arbeitsvertrag" to "Arbeitszeugnis",
            "Kreditvertrag" to "Baufinanzierungsanfrage",
            "Kontoauszüge" to "Anforderung von Kontoumsätzen",
            "Versicherungspolice" to "Versicherungsvertrag"
        )
        paare.forEach { (art, eingabe) ->
            assertFalse("$art darf $eingabe nicht akzeptieren", SynonymKatalog.passt(art, eingabe))
        }
    }

    @Test fun ocrUmbruchUmlautUndBindestrich() {
        assertTrue(SynonymKatalog.passt("Gehaltsabrechnungen", "Gehalts-\nabrechnung"))
        assertTrue(SynonymKatalog.passt("Gehaltsabrechnungen", "Bezüge Abrechnung"))
        assertTrue(SynonymKatalog.passt("Kontoauszüge", "Bankauszuege"))
        assertTrue(SynonymKatalog.passt("Mietvertrag", "Miet-Vereinbarung"))
    }

    @Test fun mehrfacheMengenUndJahreGetrennt() {
        val anforderungen = UnterlagenErkennung.anforderungenErkennen(
            "Ich benötige die letzten drei Gehaltsabrechnungen, zwei aktuelle Kontoauszüge, " +
                "meinen Arbeitsvertrag und den Steuerbescheid für 2025."
        ).associateBy { it.dokumentenart }
        assertEquals(4, anforderungen.size)
        assertEquals(3, anforderungen.getValue("Gehaltsabrechnungen").anzahl)
        assertEquals(2, anforderungen.getValue("Kontoauszüge").anzahl)
        assertEquals(2025, anforderungen.getValue("Steuerbescheid").jahr)
    }

    @Test fun anforderungsschreibenIstKeinNachweis() {
        assertFalse(AssistentSuche.passtZurAnforderung(
            "Kontoauszüge", "Anforderung Kontoauszüge.pdf",
            "Bitte reichen Sie zwei Kontoauszüge ein."
        ))
    }

    @Test fun synonymTrefferInOcrSuche() {
        assertTrue(AssistentSuche.ocrSuchePasst("Bankauszug Januar.pdf", "", "Kontoauszug"))
        assertFalse(AssistentSuche.ocrSuchePasst(
            "Anforderung.pdf", "Bitte reichen Sie Kontoauszüge ein", "Kontoauszug"
        ))
    }
}
