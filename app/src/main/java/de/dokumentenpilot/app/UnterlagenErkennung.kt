package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean,
    val jahr: Int? = null
)

object UnterlagenErkennung {

    internal val gehaltsbegriffe = listOf(
        "gehaltsabrechnung", "gehaltsabrechnungen",
        "lohnabrechnung", "lohnabrechnungen",
        "verdienstabrechnung", "verdienstabrechnungen",
        "bezügeabrechnung", "bezügeabrechnungen",
        "bezuegeabrechnung", "bezuegeabrechnungen",
        "entgeltnachweis", "entgeltnachweise",
        "entgeltabrechnung", "entgeltabrechnungen",
        "gehaltsnachweis", "gehaltsnachweise",
        "verdienstnachweis", "verdienstnachweise", "lohnzettel",
        "lohnnachweis", "lohnnachweise", "entgeltbescheinigung", "entgeltbescheinigungen"
    )

    internal fun ocrTextNormalisieren(text: String): String =
        text.lowercase()
            .replace("\u00ad", "")
            .replace(Regex("""(?<=\p{L})-\h*\R\s*(?=\p{L})"""), "")
            .replace(Regex("""\b(gehalts|lohn|verdienst|bezüge|bezuege|entgelt)[\s_-]+(?=abrechnung)"""), "$1")

    internal val zahlen = mapOf(
        "ein" to 1, "eine" to 1, "einen" to 1,
        "zwei" to 2, "drei" to 3, "vier" to 4,
        "fünf" to 5, "sechs" to 6, "sieben" to 7,
        "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwölf" to 12
    )

    private val zahl = """(?:\d{1,2}|${zahlen.keys.joinToString("|")})"""
    private val mengeDavor = Regex(
        """\b(?:(?:die\s+)?letzten?\s+)?($zahl)\b(?:\s+[^\s\d]+){0,2}\s*$"""
    )
    private val mengeDanach = Regex(
        """\b(?:der\s+)?letzten?\s+($zahl)\s+(?:monate?n?|jahre?n?|wochen?|dokumente?|nachweise?|auszüge?|abrechnungen?)\b"""
    )

    fun anforderungenErkennen(text: String): List<UnterlagenAnforderung> {
        val inhalt = ocrTextNormalisieren(text)
        val fundstellen = begriffe.mapNotNull { (art, muster) ->
            muster.find(inhalt)?.let { art to it.range }
        }
        val trennzeichen = Regex("""[;•]|[.!?](?:\s|$)""")

        return fundstellen.map { (art, fundstelle) ->
            val position = fundstelle.first
            val vorherigesEnde = fundstellen.map { it.second.last }
                .filter { it < position }.maxOrNull()?.plus(1) ?: 0
            val naechsterAnfang = fundstellen.map { it.second.first }
                .filter { it > position }.minOrNull() ?: inhalt.length

            val davor = inhalt.substring(maxOf(vorherigesEnde, position - 90), position)
                .split(trennzeichen).last().replace(Regex("""\s+"""), " ")
            val danach = inhalt.substring(
                fundstelle.last + 1, minOf(naechsterAnfang, fundstelle.last + 141)
            ).split(trennzeichen, limit = 2).first().replace(Regex("""\s+"""), " ")
            val treffer = mengeDavor.find(davor)
            val zahlwort = treffer?.groupValues?.get(1)
            val anzahlDavor = zahlwort?.toIntOrNull() ?: zahlen[zahlwort]
            val mengenNachher = mengeDanach.find(danach)
            val nachherWort = mengenNachher?.groupValues?.get(1)
            val anzahlNachher = nachherWort?.toIntOrNull() ?: zahlen[nachherWort]
            val anzahl = (anzahlNachher ?: anzahlDavor)?.takeIf { it in 1..24 }
            val neueste = mengenNachher != null ||
                treffer?.let { Regex("""\bletzten?\b""").containsMatchIn(it.value) } == true

            val jahresTreffer = Regex(
                """(?i)\b(?:aus|für|von|im|jahr|steuerjahr|veranlagungsjahr)\s+(?:dem\s+|das\s+)?(?:jahr\s+)?(?<jahr>20\d{2})\b"""
            ).find(danach)

            UnterlagenAnforderung(
                dokumentenart = art,
                anzahl = anzahl,
                neuesteZuerst = neueste,
                jahr = jahresTreffer?.groups?.get("jahr")?.value?.toIntOrNull()
            )
        }
    }

    private val begriffe = listOf(
        "Gehaltsabrechnungen" to gehaltsbegriffe,
        "Kontoauszüge" to listOf(
            "kontoauszug", "kontoauszüge", "kontoauszügen",
            "kontoumsätze", "kontoumsaetze", "kontoauszuege", "bankauszug", "bankauszüge", "bankauszuege"
        ),
        "Personalausweis" to listOf(
            "personalausweis", "ausweiskopie",
            "identitätsnachweis"
        ),
        "Arbeitsvertrag" to listOf(
            "arbeitsvertrag", "arbeitsverträge", "arbeitsvertraege", "anstellungsvertrag", "beschäftigungsvertrag", "beschaeftigungsvertrag"
        ),
        "Mietvertrag" to listOf(
            "mietvertrag", "mietverträge", "mietvertraege", "mietvereinbarung", "wohnungsmietvertrag"
        ),
        "Steuererklärung" to listOf(
            "steuererklärung",
            "steuererklaerung",
            "einkommensteuererklärung",
            "einkommensteuererklaerung"
        ),
        "Steuerbescheid" to listOf(
            "steuerbescheid", "einkommensteuerbescheid"
        ),
        "Selbstauskunft" to listOf("selbstauskunft", "selbst auskunft"),
        "Lohnsteuerbescheinigung" to listOf("lohnsteuerbescheinigung", "jahreslohnsteuerbescheinigung"),
        "Renteninformation" to listOf("renteninformation", "renteninformationen"),
        "Rentenbescheid" to listOf("rentenbescheid", "rentenbescheide"),
        "Rentenversicherungsverlauf" to listOf("rentenversicherungsverlauf", "versicherungsverlauf"),
        "Eigenkapitalnachweis" to listOf("eigenkapitalnachweis", "nachweis des vorhandenen eigenkapitals", "eigenkapitalnachweise"),
        "Kindergeldbescheid" to listOf("kindergeldbescheid", "kindergeldnachweis", "nachweis kindergeld"),
        "Kreditvertrag" to listOf("kreditvertrag", "kreditverträge", "kreditvertraege", "darlehensvertrag", "darlehensverträge", "darlehensvertraege"),
        "Bausparvertrag" to listOf("bausparvertrag", "bausparverträge", "bausparvertraege"),
        "Wohnflächenberechnung" to listOf("wohnflächenberechnung", "wohnflaechenberechnung", "wohnflächenberechnungen"),
        "Grundbuchauszug" to listOf("grundbuchauszug", "grundbuchauszüge", "grundbuchauszuege"),
        "Meldebescheinigung" to listOf("meldebescheinigung", "meldebestätigung", "meldebestaetigung"),
        "Geburtsurkunde" to listOf("geburtsurkunde", "geburtsurkunden"),
        "Krankenversicherungsnachweis" to listOf("krankenversicherungsnachweis", "krankenversicherungsbescheinigung"),
        "Versicherungspolice" to listOf("versicherungspolice", "versicherungsschein", "versicherungsvertrag"),
        "Arbeitszeugnis" to listOf("arbeitszeugnis", "arbeitszeugnisse"),
        "Nebenkostenabrechnung" to listOf("nebenkostenabrechnung", "betriebskostenabrechnung"),
        "Wohnungsgeberbestätigung" to listOf("wohnungsgeberbestätigung", "wohnungsgeberbestaetigung"),
        "Rechnung" to listOf("rechnung", "rechnungen"),
        "Aufenthaltstitel" to listOf("aufenthaltstitel", "aufenthaltserlaubnis"),
        "Führungszeugnis" to listOf("führungszeugnis", "fuehrungszeugnis")
    ).map { (art, woerter) ->
        art to Regex(
            """(?<!\p{L})(?:${woerter.joinToString("|", transform = Regex::escape)})(?!\p{L})"""
        )
    }

    fun angeforderteUnterlagen(text: String): List<String> {
        val inhalt = ocrTextNormalisieren(text)
        return begriffe.filter { (_, muster) -> muster.containsMatchIn(inhalt) }.map { it.first }
    }

    fun passtZuUnterlage(unterlage: String, text: String): Boolean {
        // Gleiche Begriffsfamilien für Anforderung und vorhandenes Dokument.
        // Verhindert, dass nur die ersten sieben Arten Synonyme besitzen.
        val erkannteArten = angeforderteUnterlagen(text)
        return unterlage in erkannteArten
    }

}
