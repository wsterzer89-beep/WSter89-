package de.dokumentenpilot.app

object UnterlagenSuche {

    fun istGehaltsabrechnung(
        dokumentName: String,
        erkannterText: String
    ): Boolean {
        val begriffe = listOf(
            "gehaltsabrechnung",
            "lohnabrechnung",
            "entgeltabrechnung",
            "verdienstabrechnung",
            "bezügeabrechnung"
        )

        val inhalt = "$dokumentName $erkannterText"

        return begriffe.any { begriff ->
            inhalt.contains(begriff, ignoreCase = true)
        }
    }
}
