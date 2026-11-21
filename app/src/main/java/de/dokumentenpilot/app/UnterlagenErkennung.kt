package de.dokumentenpilot.app

object UnterlagenErkennung {

    fun angeforderteUnterlagen(text: String): List<String> {
        val inhalt = text.lowercase()

        val begriffe = listOf(
            "Gehaltsabrechnungen" to listOf(
                "gehaltsabrechnung", "lohnabrechnung",
                "verdienstnachweis"
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
                "gehaltsabrechnung", "lohnabrechnung", "verdienstnachweis"
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
