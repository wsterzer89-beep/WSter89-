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
            "entgeltabrechnung", "verdienstabrechnung",
            "entgeltnachweis", "entgeltnachweise",
            "entgeltbescheinigung", "gehaltsnachweis",
            "lohnzettel", "lohnnachweis",
            "bezügeabrechnung", "bezuegeabrechnung"
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

    // Grobe Kategorien bleiben bestehen; fachliche Äquivalenz liefert allein
    // der Synonymkatalog. Unbegrenzte String.contains()-Treffer sind verboten.
    private val katalogTypen = mapOf(
        "MIETE" to setOf("Mietvertrag", "Nebenkostenabrechnung", "Wohnungsgeberbestätigung"),
        "STEUER" to setOf("Steuerbescheid", "Steuererklärung", "Lohnsteuerbescheinigung"),
        "MEDIZIN" to setOf("Arztbrief", "Laborbefund", "Arbeitsunfähigkeitsbescheinigung"),
        "AUSWEIS" to setOf("Personalausweis", "Aufenthaltstitel", "Führerschein"),
        "GEHALT" to setOf("Gehaltsabrechnungen"),
        "RECHNUNG" to setOf("Rechnung", "Stromrechnung", "Nebenkostenabrechnung"),
        "BESCHEID" to setOf("Steuerbescheid", "Rentenbescheid", "Kindergeldbescheid"),
        "KONTOAUSZUG" to setOf("Kontoauszüge"),
        "VERSICHERUNG" to setOf("Versicherungspolice", "Krankenversicherungsnachweis"),
        "VERTRAG" to setOf("Arbeitsvertrag", "Mietvertrag", "Kreditvertrag", "Bausparvertrag")
    )

    fun suchSynonyme(wort: String): Set<String> = SynonymKatalog.synonyme(wort)

    fun passendeTypen(text: String): Set<String> {
        val erkannt = SynonymKatalog.erkannteArten(text).toSet()
        val normal = SynonymKatalog.normalisieren(text)
        val ergebnis = linkedSetOf<String>()
        katalogTypen.forEach { (typ, arten) ->
            if (arten.any { it in erkannt }) ergebnis.add(typ)
        }
        begriffe.forEach { (typ, woerter) ->
            if (woerter.any { wort ->
                    val muster = Regex("""(?<![\\p{L}\\p{N}])${Regex.escape(SynonymKatalog.normalisieren(wort))}(?![\\p{L}\\p{N}])""")
                    muster.containsMatchIn(normal)
                }
            ) ergebnis.add(typ)
        }
        return ergebnis
    }

    fun erkennen(text: String): String? {
        val typen = passendeTypen(text)
        // Spezifische Gehaltsabrechnungen nicht als generische Rechnung werten.
        return if ("GEHALT" in typen) "GEHALT" else typen.firstOrNull()
    }
}
