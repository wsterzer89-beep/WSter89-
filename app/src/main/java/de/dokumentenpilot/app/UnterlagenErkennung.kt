package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean,
    val jahr: Int? = null
)

object UnterlagenErkennung {


    fun anforderungenErkennen(text: String): List<UnterlagenAnforderung> {
        val zahlen = mapOf(
            "ein" to 1, "eine" to 1, "einen" to 1,
            "zwei" to 2, "drei" to 3, "vier" to 4,
            "fünf" to 5, "sechs" to 6, "sieben" to 7,
            "acht" to 8, "neun" to 9, "zehn" to 10,
            "elf" to 11, "zwölf" to 12
        )

        val begriffe = mapOf(
            "Gehaltsabrechnungen" to listOf(
                "gehaltsabrechnung", "gehaltsabrechnungen",
                "lohnabrechnung", "lohnabrechnungen",
                "entgeltnachweis", "entgeltnachweise",
                "entgeltabrechnung", "entgeltabrechnungen",
                "gehaltsnachweis", "gehaltsnachweise",
                "lohnzettel", "verdienstnachweis", "verdienstnachweise"
            ),
            "Kontoauszüge" to listOf(
                "kontoauszug", "kontoauszüge", "kontoauszügen",
                "kontoumsätze"
            )
        )

        val inhalt = text.lowercase()

        return angeforderteUnterlagen(text).map { art ->
            val suchwoerter = begriffe[art] ?: listOf(art.lowercase())

            val position = suchwoerter.mapNotNull { wort ->
                Regex("""(?<![\p{L}])""" + Regex.escape(wort) + """(?![\p{L}])""")
                    .find(inhalt)?.range?.first
            }.minOrNull()

            val treffer = if (position != null) {
                val anfang = maxOf(0, position - 90)
                val davor = inhalt.substring(anfang, position)
                    .replace(Regex("""\s+"""), " ")
                val muster = Regex(
                    """(?i)\b(?:(?:die\s+)?letzten?\s+)?""" +
                    """(\d{1,2}|ein|eine|einen|zwei|drei|vier|fünf|""" +
                    """sechs|sieben|acht|neun|zehn|elf|zwölf)""" +
                    """(?:\s+\S+){0,2}\s*$"""
                )
                muster.find(davor)
            } else null

            val zahlwort = treffer?.groupValues?.get(1)?.lowercase()
            val anzahlDavor = zahlwort?.toIntOrNull() ?: zahlen[zahlwort]

            // OCR kann "der letzten 3 Monate" auf mehrere Zeilen verteilen.
            // Am nächsten Satz-/Listenpunkt stoppen, nicht am Zeilenumbruch.
            val zeile = if (position != null) {
                inhalt.substring(position).take(140)
                    .substringBefore(';').substringBefore('•')
                    .replace(Regex("""\s+"""), " ")
            } else ""
            val mengenNachher = Regex(
                """(?i)\b(?:der\s+)?letzten?\s+(\d{1,2}|ein|eine|einen|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\s+(?:monate?|jahre?|wochen?|dokumente?|nachweise?|auszüge?|abrechnungen?)\b"""
            ).find(zeile)
            val nachherWort = mengenNachher?.groupValues?.get(1)?.lowercase()
            val anzahlNachher = nachherWort?.toIntOrNull() ?: zahlen[nachherWort]
            val anzahl = (anzahlNachher ?: anzahlDavor)?.takeIf { it in 1..24 }
            val neueste = (treffer != null &&
                Regex("""(?i)\bletzten?\b""").containsMatchIn(treffer.value)) ||
                mengenNachher != null

            val danach = if (position != null) {
                inhalt.substring(
                    position,
                    minOf(inhalt.length, position + 140)
                ).substringBefore(';').substringBefore('•')
                    .replace(Regex("""\s+"""), " ")
            } else ""

            val jahresTreffer = Regex(
                """(?i)\b(?:aus|für|von|im|jahr|steuerjahr|veranlagungsjahr)\s+(?:dem\s+|das\s+)?(?:jahr\s+)?(20\d{2})\b"""
            ).find(danach)

            val kontoauszugMonate = if (art == "Kontoauszüge" && position != null) {
                val abschnitt = inhalt.substring(
                    position,
                    minOf(inhalt.length, position + 140)
                ).substringBefore(';').substringBefore('•')
                    .replace(Regex("""\s+"""), " ")

                val trefferMonate = Regex(
                    """(?i)\b(?:der\s+)?letzten?\s+(\d{1,2}|ein|eine|einen|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\s+monate?\b"""
                ).find(abschnitt)

                trefferMonate?.groupValues?.get(1)?.let { wort ->
                    wort.toIntOrNull() ?: zahlen[wort.lowercase()]
                }
            } else null

            UnterlagenAnforderung(
                dokumentenart = art,
                anzahl = kontoauszugMonate ?: anzahl,
                neuesteZuerst = if (kontoauszugMonate != null) true else neueste,
                jahr = jahresTreffer?.groupValues?.get(1)?.toIntOrNull()
            )
        }
    }

    fun angeforderteUnterlagen(text: String): List<String> {
        val inhalt = text.lowercase()

        val begriffe = listOf(
            "Gehaltsabrechnungen" to listOf(
                "gehaltsabrechnung", "gehaltsabrechnungen", "lohnabrechnung", "lohnabrechnungen", "entgeltnachweis", "entgeltnachweise", "entgeltabrechnung", "gehaltsnachweis", "lohnzettel",
                "verdienstnachweis", "verdienstnachweise"
            ),
            "Kontoauszüge" to listOf(
                "kontoauszug", "kontoauszüge",
                "kontoumsätze"
            ),
            "Personalausweis" to listOf(
                "personalausweis", "ausweiskopie",
                "identitätsnachweis"
            ),
            "Arbeitsvertrag" to listOf(
                "arbeitsvertrag"
            ),
            "Mietvertrag" to listOf(
                "mietvertrag"
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
            "Renteninformation" to listOf("renteninformation", "rentenbescheid", "rentenversicherungsverlauf"),
            "Eigenkapitalnachweis" to listOf("eigenkapitalnachweis", "nachweis des vorhandenen eigenkapitals", "eigenkapitalnachweise"),
            "Kindergeldbescheid" to listOf("kindergeldbescheid", "kindergeldnachweis", "nachweis kindergeld"),
            "Kreditvertrag" to listOf("kreditvertrag", "kreditverträge", "kreditvertraege", "baufinanzierung", "darlehensvertrag"),
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
            "Rechnung" to listOf("rechnung", "rechnungen", "zahlungsaufforderung"),
            "Aufenthaltstitel" to listOf("aufenthaltstitel", "aufenthaltserlaubnis"),
            "Führungszeugnis" to listOf("führungszeugnis", "fuehrungszeugnis")
        )

        return begriffe
            .filter { (_, suchwoerter) ->
                suchwoerter.any { wort ->
                    Regex("""(?<![\p{L}])""" + Regex.escape(wort) + """(?![\p{L}])""")
                        .containsMatchIn(inhalt)
                }
            }
            .map { it.first }
    }

    fun passtZuUnterlage(unterlage: String, text: String): Boolean {
        // Gleiche Begriffsfamilien für Anforderung und vorhandenes Dokument.
        // Verhindert, dass nur die ersten sieben Arten Synonyme besitzen.
        val erkannteArten = angeforderteUnterlagen(text)
        return unterlage in erkannteArten
    }

}
