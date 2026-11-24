package de.dokumentenpilot.app

/**
 * Fachlich äquivalente Dokumentbezeichnungen. Keine allgemeine Levenshtein-
 * oder Teilstring-Suche: insbesondere Bescheide, Auskünfte und Anforderungen
 * dürfen nicht mit den eigentlichen Nachweisen verwechselt werden.
 *
 * Für neue Begriffe nur die entsprechende fachliche Familie erweitern.
 * Die Anzeigenamen bleiben mit bestehenden Mappen/Checklisten kompatibel.
 */
object SynonymKatalog {
    val familien: Map<String, Set<String>> = linkedMapOf(
        "Gehaltsabrechnungen" to setOf(
            "gehaltsabrechnung", "gehaltsabrechnungen", "lohnabrechnung",
            "lohnabrechnungen", "verdienstabrechnung", "verdienstabrechnungen",
            "bezügeabrechnung", "bezügeabrechnungen", "entgeltabrechnung",
            "entgeltabrechnungen", "entgeltnachweis", "entgeltnachweise",
            "gehaltsnachweis", "gehaltsnachweise", "verdienstnachweis",
            "verdienstnachweise", "lohnnachweis", "lohnnachweise", "lohnzettel",
            "entgeltbescheinigung", "entgeltbescheinigungen",
            "payslip", "payslips", "salary slip", "salary statement"
        ),
        "Kontoauszüge" to setOf(
            "kontoauszug", "kontoauszüge", "kontoauszügen",
            "bankauszug", "bankauszüge", "bankauszügen",
            "bank statement", "bank statements"
        ),
        "Personalausweis" to setOf("personalausweis", "ausweiskopie", "identitätsnachweis", "identity card"),
        "Arbeitsvertrag" to setOf("arbeitsvertrag", "arbeitsverträge", "anstellungsvertrag",
            "anstellungsverträge", "beschäftigungsvertrag", "beschäftigungsverträge", "employment contract"),
        "Mietvertrag" to setOf("mietvertrag", "mietverträge", "mietvereinbarung",
            "mietvereinbarungen", "wohnungsmietvertrag", "rental agreement", "tenancy agreement"),
        "Steuererklärung" to setOf("steuererklärung", "steuererklärungen", "einkommensteuererklärung",
            "einkommensteuererklärungen", "tax return"),
        "Steuerbescheid" to setOf("steuerbescheid", "steuerbescheide",
            "einkommensteuerbescheid", "einkommensteuerbescheide", "tax assessment"),
        "Selbstauskunft" to setOf("selbstauskunft", "selbst auskunft"),
        "Lohnsteuerbescheinigung" to setOf("lohnsteuerbescheinigung", "jahreslohnsteuerbescheinigung",
            "elektronische lohnsteuerbescheinigung"),
        "Renteninformation" to setOf("renteninformation", "renteninformationen"),
        "Rentenbescheid" to setOf("rentenbescheid", "rentenbescheide"),
        "Rentenversicherungsverlauf" to setOf("rentenversicherungsverlauf", "versicherungsverlauf",
            "rentenversicherungsverläufe"),
        "Eigenkapitalnachweis" to setOf("eigenkapitalnachweis",
            "eigenkapitalnachweise", "nachweis des vorhandenen eigenkapitals"),
        "Kindergeldbescheid" to setOf("kindergeldbescheid", "kindergeldbescheide"),
        "Kreditvertrag" to setOf("kreditvertrag", "kreditverträge",
            "darlehensvertrag", "darlehensverträge", "loan agreement"),
        "Bausparvertrag" to setOf("bausparvertrag", "bausparverträge"),
        "Wohnflächenberechnung" to setOf("wohnflächenberechnung", "wohnflächenberechnungen"),
        "Grundbuchauszug" to setOf("grundbuchauszug", "grundbuchauszüge"),
        "Meldebescheinigung" to setOf("meldebescheinigung", "meldebescheinigungen",
            "meldebestätigung", "meldebestätigungen"),
        "Geburtsurkunde" to setOf("geburtsurkunde", "geburtsurkunden", "birth certificate"),
        "Krankenversicherungsnachweis" to setOf("krankenversicherungsnachweis",
            "krankenversicherungsbescheinigung", "versicherungsnachweis krankenversicherung"),
        "Versicherungspolice" to setOf("versicherungspolice", "versicherungspolicen",
            "versicherungsschein", "versicherungsscheine", "insurance policy"),
        "Arbeitszeugnis" to setOf("arbeitszeugnis", "arbeitszeugnisse", "arbeitsreferenz"),
        "Nebenkostenabrechnung" to setOf("nebenkostenabrechnung", "nebenkostenabrechnungen",
            "betriebskostenabrechnung", "betriebskostenabrechnungen"),
        "Wohnungsgeberbestätigung" to setOf("wohnungsgeberbestätigung", "wohnungsgeberbestätigungen"),
        "Rechnung" to setOf("rechnung", "rechnungen", "invoice", "invoices"),
        "Aufenthaltstitel" to setOf("aufenthaltstitel", "aufenthaltserlaubnis"),
        "Führungszeugnis" to setOf("führungszeugnis", "polizeiliches führungszeugnis"),
        "Fahrzeugschein" to setOf("fahrzeugschein", "zulassungsbescheinigung teil i"),
        "Fahrzeugbrief" to setOf("fahrzeugbrief", "zulassungsbescheinigung teil ii"),
        "Führerschein" to setOf("führerschein", "fahrerlaubnis", "driving licence"),
        "Arztbrief" to setOf("arztbrief", "arztbriefe", "ärztlicher bericht"),
        "Laborbefund" to setOf("laborbefund", "laborbefunde", "laborbericht"),
        "Arbeitsunfähigkeitsbescheinigung" to setOf("arbeitsunfähigkeitsbescheinigung",
            "arbeitsunfähigkeitsbescheinigungen", "krankmeldung", "au bescheinigung"),
        "Stromrechnung" to setOf("stromrechnung", "stromabrechnung", "stromkostenabrechnung")
    )

    /** OCR-Zeilenumbrüche, Umlautvarianten und getrennte Komposita normalisieren. */
    fun normalisieren(text: String): String =
        text.lowercase(java.util.Locale.ROOT)
            .replace("\u00ad", "")
            .replace(Regex("""(?<=\p{L})-\h*\R\s*(?=\p{L})"""), "")
            .replace(Regex("""(?<=\p{L})[ _-]+(?=abrechnungen?\b)"""), "")
            .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace('ß', 's')
            .replace("ae", "a").replace("oe", "o").replace("ue", "u")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun muster(begriff: String): Regex {
        val teile = normalisieren(begriff).split(' ').filter { it.isNotBlank() }
        val pattern = teile.joinToString("""[\s_-]+""") { Regex.escape(it) }
        return Regex("""(?<![\p{L}\p{N}])$pattern(?![\p{L}\p{N}])""")
    }

    private val musterNachArt: Map<String, List<Regex>> =
        familien.mapValues { (_, woerter) -> woerter.map(::muster) }

    fun passt(art: String, text: String): Boolean {
        val normal = normalisieren(text)
        return musterNachArt[art]?.any { it.containsMatchIn(normal) } ?: false
    }

    fun fundstellen(text: String): List<Pair<String, IntRange>> {
        val normal = normalisieren(text)
        return musterNachArt.flatMap { (art, muster) ->
            muster.flatMap { regex -> regex.findAll(normal).map { art to it.range }.toList() }
        }.sortedWith(compareBy<Pair<String, IntRange>> { it.second.first }
            .thenByDescending { it.second.last - it.second.first })
            .fold(mutableListOf<Pair<String, IntRange>>()) { result, treffer ->
                // Überlappende Synonyme derselben Art nicht doppelt zählen.
                if (result.none { it.first == treffer.first && it.second.first == treffer.second.first }) {
                    result.add(treffer)
                }
                result
            }
    }

    fun erkannteArten(text: String): List<String> = fundstellen(text).map { it.first }.distinct()

    fun synonyme(wort: String): Set<String> {
        val normal = normalisieren(wort)
        val familie = familien.entries.firstOrNull { (_, begriffe) ->
            begriffe.any { normalisieren(it) == normal }
        } ?: return setOf(wort)
        return familie.value
    }
}
