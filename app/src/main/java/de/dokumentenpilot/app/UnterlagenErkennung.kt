package de.dokumentenpilot.app


data class UnterlagenAnforderung(
    val dokumentenart: String,
    val anzahl: Int?,
    val neuesteZuerst: Boolean
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

            UnterlagenAnforderung(
                dokumentenart = art,
                anzahl = anzahl,
                neuesteZuerst = neueste
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
