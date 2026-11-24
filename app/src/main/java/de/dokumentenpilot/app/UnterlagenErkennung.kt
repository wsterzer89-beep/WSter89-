package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean,
    val jahr: Int? = null,
    val monatVon: Int? = null,
    val monatBis: Int? = null
)

object UnterlagenErkennung {

    internal val gehaltsbegriffe: List<String> =
        SynonymKatalog.familien.getValue("Gehaltsabrechnungen").toList()

    internal fun ocrTextNormalisieren(text: String): String =
        SynonymKatalog.normalisieren(text)

    internal val zahlen = mapOf(
        "ein" to 1, "eine" to 1, "einen" to 1,
        "zwei" to 2, "drei" to 3, "vier" to 4,
        "fünf" to 5, "fuenf" to 5, "sechs" to 6, "sieben" to 7,
        "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwölf" to 12, "zwoelf" to 12
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
        val fundstellen = SynonymKatalog.fundstellen(inhalt).distinctBy { it.first }
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
                """(?i)\b(?:aus|für|fuer|von|im|jahr|steuerjahr|veranlagungsjahr)\s+(?:dem\s+|das\s+)?(?:jahr\s+)?(?<jahr>20\d{2})\b"""
            ).find(danach)

            val zeitraum = MonatsZeitraum.erkennen(danach)
            UnterlagenAnforderung(
                dokumentenart = art,
                anzahl = anzahl,
                neuesteZuerst = neueste,
                jahr = if (zeitraum == null) jahresTreffer?.groups?.get("jahr")?.value?.toIntOrNull() else null,
                monatVon = zeitraum?.first,
                monatBis = zeitraum?.last
            )
        }
    }

    fun angeforderteUnterlagen(text: String): List<String> {
        val inhalt = ocrTextNormalisieren(text)
        return SynonymKatalog.erkannteArten(inhalt)
    }

    fun passtZuUnterlage(unterlage: String, text: String): Boolean {
        // Gleiche Begriffsfamilien für Anforderung und vorhandenes Dokument.
        // Verhindert, dass nur die ersten sieben Arten Synonyme besitzen.
        return SynonymKatalog.passt(unterlage, text)
    }

}
