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

        val abrechnungen = UnterlagenErkennung.anforderungenErkennen(text)
            .firstOrNull { it.dokumentenart == "Gehaltsabrechnungen" }
        if (abrechnungen != null) {
            val letzte = Regex("""\b(?:letzt|neuest)e[nrs]?\b""").containsMatchIn(text)
            val anzahl = abrechnungen.anzahl ?: 3.takeIf { letzte }
            return AssistentAnfrage("Gehaltsabrechnungen", anzahl, eingabe.trim())
        }

        val ziffer = Regex("""\b(\d{1,2})\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

        val wortZahl = UnterlagenErkennung.zahlen.entries.firstOrNull {
            Regex("""\b${it.key}\b""").containsMatchIn(text)
        }?.value

        val anzahl = (ziffer ?: wortZahl)?.coerceIn(1, 20)

        return AssistentAnfrage(
            dokumentTyp = "ALLE",
            anzahl = anzahl,
            beschreibung = eingabe.trim()
        )
    }
}
