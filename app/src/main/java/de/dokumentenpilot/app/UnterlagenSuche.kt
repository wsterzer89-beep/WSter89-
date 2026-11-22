package de.dokumentenpilot.app

object UnterlagenSuche {

    fun istGehaltsabrechnung(
        dokumentName: String,
        erkannterText: String
    ): Boolean {
        val name = dokumentName.lowercase()
        val text = erkannterText.lowercase()
        val inhalt = "$name $text"

        val brutto = listOf(
            "gesamtbrutto", "bruttogehalt",
            "bruttoverdienst", "steuerbrutto"
        ).any { text.contains(it) }

        val netto = listOf(
            "nettoverdienst", "nettogehalt",
            "nettoauszahlung", "nettobezug"
        ).any { text.contains(it) }

        val abrechnungszeitraum = listOf(
            "abrechnungsmonat", "abrechnungszeitraum",
            "abrechnung für", "abrechnungsperiode"
        ).any { text.contains(it) }

        val echteAbrechnungsmerkmale =
            listOf(brutto, netto, abrechnungszeitraum)
                .count { it }

        val anforderung = listOf(
            "bitte reichen sie",
            "reichen sie",
            "bitte senden sie",
            "legen sie",
            "benötigen wir",
            "folgende unterlagen"
        ).any { text.contains(it) }

        val geldbetrag = Regex(
            """\b\d{1,3}(?:\.\d{3})*,\d{2}\b"""
        ).containsMatchIn(text)

        if (echteAbrechnungsmerkmale >= 2 && geldbetrag) {
            return true
        }

        if (anforderung) {
            return false
        }

        return UnterlagenErkennung.passtZuUnterlage(
            "Gehaltsabrechnungen",
            inhalt
        ) || listOf(
            "verdienstabrechnung",
            "bezügeabrechnung"
        ).any { inhalt.contains(it) }
    }
}
