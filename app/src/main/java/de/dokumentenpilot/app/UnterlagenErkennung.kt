package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean,
    val jahr: Int? = null,
    val monatVon: Int? = null,
    val monatBis: Int? = null,
    val namensFilter: String? = null
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

    // Explizite Mengen in Klammern oder nach dem Dokumentnamen.
    // Monatsangaben werden nur als Anzahl bei monatlichen Gehaltsnachweisen verwendet.
    private val periodenZahlen = buildMap<String, Int> {
        zahlen.forEach { (wort, wert) -> put(ocrTextNormalisieren(wort), wert) }
        listOf(
            "one two three four five six seven eight nine ten",
            "un deux trois quatre cinq six sept huit neuf dix",
            "uno dos tres cuatro cinco seis siete ocho nueve diez",
            "jeden dwa trzy cztery pięć sześć siedem osiem dziewięć dziesięć",
            "один два три четыре пять шесть семь восемь девять десять",
            "bir iki üç dört beş altı yedi sekiz dokuz on",
            "один два три чотири п’ять шість сім вісім дев’ять десять",
            "واحد اثنان ثلاثة أربعة خمسة ستة سبعة ثمانية تسعة عشرة"
        ).forEach { reihe ->
            reihe.split(' ').forEachIndexed { index, wort ->
                put(ocrTextNormalisieren(wort), index + 1)
            }
        }
    }
    private val periodenZahl = """(?:\d{1,2}|${periodenZahlen.keys.joinToString("|") { Regex.escape(it) }})"""
    private fun periodenAnzahl(treffer: MatchResult?): Int? =
        treffer?.groupValues?.get(1)?.let { it.toIntOrNull() ?: periodenZahlen[it] }

    private val monatsMenge = Regex(
        """(?<![\p{L}\p{N}])($periodenZahl)\s+(?:monate?n?|months?|mois|meses|miesiące|miesięcy|месяц(?:а|ев)?|ay|місяц(?:і|ів|ь)|أشهر|شهور)(?!\p{L})"""
    )

    private val jahresMenge = Regex(
        """(?<![\p{L}\p{N}])($periodenZahl)\s+(?:jahre?n?|years?|ans|années|años|lat|lata|года|лет|yıl|роки|років|سنوات|أعوام)(?!\p{L})"""
    )

    fun anforderungenErkennen(text: String): List<UnterlagenAnforderung> {
        val inhalt = ocrTextNormalisieren(text).replace(Regex("""\b(?:mind|min)\.\s*"""), "mindestens ")
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
            val monatsAnzahl = if (art == "Gehaltsabrechnungen")
                periodenAnzahl(monatsMenge.find(danach)) else null
            val jahresAnzahl = if (art in setOf("Steuererklärung", "Steuerbescheid", "Jahresabschluss"))
                periodenAnzahl(jahresMenge.find(danach)) else null
            val anzahl = (anzahlNachher ?: monatsAnzahl ?: jahresAnzahl ?: anzahlDavor)?.takeIf { it in 1..24 }
            val neueste = mengenNachher != null || monatsAnzahl != null || jahresAnzahl != null ||
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
                monatBis = zeitraum?.last,
                namensFilter = Regex("""\b(?:person|arbeitgeber|kontoinhaber)\s*[:=]\s*["„]([^"“”]{2,100})["“”]""")
                    .find(danach)?.groupValues?.get(1)?.trim()
            )
        }
    }

    data class Listenpunkt(
        val original: String,
        val anforderungen: List<UnterlagenAnforderung>,
        val bedingt: Boolean,
        val spaeter: Boolean,
        val alternative: Boolean = false
    )

    fun alternativeErfuellt(punkt: Listenpunkt, trefferAnzahl: (UnterlagenAnforderung) -> Int): Boolean =
        einfacheAlternative(punkt) && punkt.anforderungen.any {
            trefferAnzahl(it) >= (it.anzahl ?: 1)
        }

    // Nur eindeutige reine Oder-Alternativen ohne weitere Bedingungen automatisch bewerten.
    fun einfacheAlternative(punkt: Listenpunkt): Boolean {
        if (!punkt.alternative || punkt.bedingt || punkt.spaeter || punkt.anforderungen.size < 2) return false
        val normal = ocrTextNormalisieren(punkt.original)
        val teile = normal.split(Regex("""\s+(?:oder|or|ou|lub|albo|или|veya|або|أو)\s+"""))
        if (teile.size != punkt.anforderungen.size) return false
        return teile.zip(punkt.anforderungen).all { (teil, anforderung) ->
            // Keine zusätzlichen unbekannten Anforderungen oder gemischten Verknüpfungen.
            SynonymKatalog.familien.getValue(anforderung.dokumentenart).any {
                ocrTextNormalisieren(it) == teil.trim()
            }
        }
    }

    // Nur explizit strukturierte Listen; freie Suchanfragen bleiben unverändert.
    fun listenpunkteErkennen(text: String): List<Listenpunkt> {
        val marker = Regex("""^\s*(?:[•●▪☐☑□✓✔–-]|\d{1,2}[.)])\s+""")
        val punkte = mutableListOf<StringBuilder>()
        var aktuell: StringBuilder? = null
        for (zeile in text.lines()) {
            if (marker.containsMatchIn(zeile)) {
                aktuell = StringBuilder(zeile.replaceFirst(marker, "").trim())
                punkte.add(aktuell)
            } else if (zeile.isBlank()) {
                // OCR kann innerhalb eines Listenpunkts leere Blocktrenner erzeugen.
                // Ein eingerückter Folgeabsatz darf den Punkt fortsetzen.
                aktuell?.append("\n")
            } else {
                if (aktuell?.endsWith("\n") == true && zeile.firstOrNull()?.isWhitespace() != true) {
                    aktuell = null
                } else {
                    aktuell?.append("\n")?.append(zeile.trim())
                }
            }
        }
        if (punkte.size < 2) return emptyList()
        return punkte.mapNotNull { punkt ->
            val original = punkt.toString().trim()
            if (original.isBlank()) return@mapNotNull null
            val normal = ocrTextNormalisieren(original)
            Listenpunkt(
                original,
                anforderungenErkennen(original),
                Regex("""\b(?:ggf|gegebenenfalls|falls|bei bedarf|bei bestandsobjekten|if applicable|if required|si nécessaire|le cas échéant|si procede|si es necesario|w razie potrzeby|jeśli dotyczy|при необходимости|если применимо|gerektiğinde|varsa|за потреби|якщо застосовно|عند الحاجة|إن وجد)\b""").containsMatchIn(normal),
                Regex("""\b(?:folgt spaeter|wird nachgereicht|to follow|provided later|à fournir ultérieurement|sera fourni ultérieurement|se entregará más adelante|pendiente de entrega|zostanie dostarczone później|будет предоставлено позже|daha sonra sunulacak|буде надано пізніше|سيقدم لاحقًا|يقدم لاحقا)\b""").containsMatchIn(normal),
                Regex("""\b(?:alternativ|oder|alternatively|or|ou|o|lub|albo|или|veya|або|أو)\b""").containsMatchIn(normal)
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
