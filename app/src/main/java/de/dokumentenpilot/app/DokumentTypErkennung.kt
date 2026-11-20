package de.dokumentenpilot.app

object DokumentTypErkennung {

    private val begriffe = mapOf(
        "MIETE" to listOf(
            "mietvertrag", "mietbescheinigung",
            "nebenkostenabrechnung", "betriebskostenabrechnung",
            "mieterhöhung", "wohnungsgeberbestätigung"
        ),
        "STEUER" to listOf(
            "steuerbescheid", "steuererklärung",
            "einkommensteuer", "lohnsteuerbescheinigung",
            "finanzamt", "steueridentifikationsnummer"
        ),
        "MEDIZIN" to listOf(
            "arztbrief", "befundbericht", "laborbefund",
            "krankenhausbericht", "entlassungsbericht",
            "arbeitsunfähigkeitsbescheinigung"
        ),
        "MAHNUNG" to listOf(
            "zahlungserinnerung", "mahnung",
            "mahnbescheid", "zahlungsrückstand",
            "inkassoschreiben"
        ),
        "AUSWEIS" to listOf(
            "personalausweis", "reisepass",
            "führerschein", "aufenthaltstitel"
        ),
        "GEHALT" to listOf(
            "gehaltsabrechnung", "lohnabrechnung",
            "entgeltabrechnung", "verdienstabrechnung"
        ),
        "RECHNUNG" to listOf(
            "rechnung", "rechnungsnummer",
            "rechnungsbetrag"
        ),
        "BESCHEID" to listOf(
            "bescheid", "bewilligungsbescheid",
            "steuerbescheid"
        ),
        "KONTOAUSZUG" to listOf(
            "kontoauszug", "kontoauszüge",
            "kontoumsätze"
        ),
        "VERSICHERUNG" to listOf(
            "versicherungsschein", "police",
            "versicherungsvertrag"
        ),
        "VERTRAG" to listOf(
            "vertrag", "vertragsnummer",
            "vertragsbeginn"
        )
    )

    fun erkennen(text: String): String? {
        val inhalt = text.lowercase()

        return begriffe.entries
            .map { (typ, woerter) ->
                typ to woerter.count { inhalt.contains(it) }
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}
