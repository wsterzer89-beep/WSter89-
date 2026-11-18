package de.dokumentenpilot.app

data class AssistentAnfrage(
    val dokumentTyp: String,
    val anzahl: Int,
    val beschreibung: String
)

object AssistentErkennung {

    fun erkennen(text: String): AssistentAnfrage? {
        val eingabe = text.lowercase()

        val gehaltsabrechnung = listOf(
            "gehaltsabrechnung",
            "gehaltsabrechnungen",
            "lohnabrechnung",
            "lohnabrechnungen",
            "verdienstabrechnung",
            "verdienstabrechnungen",
            "einkommensnachweis",
            "einkommensnachweise"
        ).any { eingabe.contains(it) }

        if (gehaltsabrechnung) {
            val ziffer = Regex("""\b(\d{1,2})\b""")
                .find(eingabe)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

            val anzahl = ziffer ?: when {
                "drei" in eingabe -> 3
                "zwei" in eingabe -> 2
                "eine" in eingabe -> 1
                else -> 3
            }

            return AssistentAnfrage(
                dokumentTyp = "GEHALT",
                anzahl = anzahl.coerceIn(1, 24),
                beschreibung = "Die letzten $anzahl Gehaltsabrechnungen"
            )
        }

        return null
    }
}
