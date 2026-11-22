package de.dokumentenpilot.app

object UnterlagenSuche {

    fun istGehaltsabrechnung(
        dokumentName: String,
        erkannterText: String
    ): Boolean {
        val inhalt = "$dokumentName $erkannterText"

        return UnterlagenErkennung.passtZuUnterlage(
            "Gehaltsabrechnungen",
            inhalt
        ) || listOf(
            "verdienstabrechnung",
            "bezügeabrechnung"
        ).any { begriff ->
            inhalt.contains(begriff, ignoreCase = true)
        }
    }
}
