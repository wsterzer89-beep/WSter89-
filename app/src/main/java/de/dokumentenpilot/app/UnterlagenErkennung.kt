package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean,
    val jahr: Int? = null,
    val startMonat: Int? = null,
    val endMonat: Int? = null
)

object UnterlagenErkennung {


    private fun monatszeitraumErkennen(text: String): Pair<Int, Int>? {
        val monate = mapOf(
            "januar" to 1, "jan" to 1,
            "februar" to 2, "feb" to 2,
            "märz" to 3, "maerz" to 3, "mrz" to 3,
            "april" to 4, "apr" to 4,
            "mai" to 5,
            "juni" to 6, "jun" to 6,
            "juli" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9,
            "oktober" to 10, "okt" to 10,
            "november" to 11, "nov" to 11,
            "dezember" to 12, "dez" to 12
        )

        val monatsnamen = monate.keys
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        val muster = Regex(
            """(?i)\b($monatsnamen)\s+(?:bis|[-–])\s+($monatsnamen)\s+(20\d{2})\b"""
        )

        val treffer = muster.find(text) ?: return null
        val start = monate[treffer.groupValues[1].lowercase()] ?: return null
        val ende = monate[treffer.groupValues[2].lowercase()] ?: return null
        val jahr = treffer.groupValues[3].toInt()

        if (start > ende) return null

        return (jahr * 100 + start) to (jahr * 100 + ende)
    }

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
                val fund = inhalt.indexOf(wort)
                if (fund >= 0) fund else null
            }.minOrNull()

            val treffer = if (position != null) {
                val anfang = maxOf(0, position - 60)
                val davor = inhalt.substring(anfang, position)
                val muster = Regex(
                    """(?i)\b(?:(?:die\s+)?letzten?\s+)?""" +
                    """(\d{1,2}|ein|eine|einen|zwei|drei|vier|fünf|""" +
                    """sechs|sieben|acht|neun|zehn|elf|zwölf)""" +
                    """(?:\s+\S+){0,2}\s*$"""
                )
                muster.find(davor)
            } else null

            val zahlwort = treffer?.groupValues?.get(1)?.lowercase()
            val anzahl = zahlwort?.toIntOrNull() ?: zahlen[zahlwort]

            val neueste = treffer != null &&
                Regex("""(?i)\bletzten?\b""")
                    .containsMatchIn(treffer.value)

            val danach = if (position != null) {
                inhalt.substring(
                    position,
                    minOf(inhalt.length, position + 100)
                ).substringBefore('\n').substringBefore('\r')
            } else ""

            val jahresTreffer = Regex(
                """(?i)\b(?:aus|für|von|im|jahr|steuerjahr|veranlagungsjahr)\s+(?:dem\s+)?(?:jahr\s+)?(20\d{2})\b"""
            ).find(danach)

            val zeitraum = if (art == "Kontoauszüge") {
                monatszeitraumErkennen(danach)
            } else null

            UnterlagenAnforderung(
                dokumentenart = art,
                anzahl = if (zeitraum != null) null else anzahl,
                neuesteZuerst = neueste,
                jahr = jahresTreffer?.groupValues?.get(1)?.toIntOrNull(),
                startMonat = zeitraum?.first,
                endMonat = zeitraum?.second
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
            "Steuerbescheid" to listOf(
                "steuerbescheid", "einkommensteuerbescheid"
            )
        )

        return begriffe
            .filter { (_, suchwoerter) ->
                suchwoerter.any { inhalt.contains(it) }
            }
            .map { it.first }
    }

    fun passtZuUnterlage(unterlage: String, text: String): Boolean {
        val suchbegriffe = when (unterlage) {
            "Gehaltsabrechnungen" -> listOf(
                "gehaltsabrechnung", "gehaltsabrechnungen", "lohnabrechnung", "lohnabrechnungen", "entgeltnachweis", "entgeltnachweise", "entgeltabrechnung", "gehaltsnachweis", "lohnzettel", "verdienstnachweis", "verdienstnachweise"
            )
            "Kontoauszüge" -> listOf(
                "kontoauszug", "kontoauszüge", "kontoauszügen", "kontoumsätze"
            )
            "Personalausweis" -> listOf(
                "personalausweis", "ausweiskopie", "identitätsnachweis"
            )
            "Arbeitsvertrag" -> listOf("arbeitsvertrag")
            "Mietvertrag" -> listOf("mietvertrag")
            "Steuerbescheid" -> listOf(
                "steuerbescheid", "einkommensteuerbescheid"
            )
            else -> listOf(unterlage.lowercase())
        }
        val inhalt = text.lowercase()
        return suchbegriffe.any { inhalt.contains(it) }
    }

}
