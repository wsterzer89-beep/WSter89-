package de.dokumentenpilot.app

data class AssistentAnfrage(
    val dokumentTyp: String,
    val anzahl: Int?,
    val beschreibung: String
)

object AssistentErkennung {

    fun erkennen(eingabe: String): AssistentAnfrage? {
        val text = eingabe.lowercase().trim()

        if (text.isBlank()) return null

        val zahlen = mapOf(
            "ein" to 1,
            "eine" to 1,
            "einen" to 1,
            "zwei" to 2,
            "drei" to 3,
            "vier" to 4,
            "fünf" to 5,
            "sechs" to 6,
            "sieben" to 7,
            "acht" to 8,
            "neun" to 9,
            "zehn" to 10,
            "elf" to 11,
            "zwölf" to 12
        )

        val ziffer = Regex("""\b(\d{1,2})\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

        val wortZahl = zahlen.entries.firstOrNull {
            Regex("""\b${it.key}\b""").containsMatchIn(text)
        }?.value

        val anzahl = (ziffer ?: wortZahl)?.coerceIn(1, 20)

        return AssistentAnfrage(
            dokumentTyp = if (
                UnterlagenErkennung.angeforderteUnterlagen(text)
                    .contains("Gehaltsabrechnungen")
            ) "Gehaltsabrechnungen" else "ALLE",
            anzahl = anzahl,
            beschreibung = eingabe.trim()
        )
    }
}
