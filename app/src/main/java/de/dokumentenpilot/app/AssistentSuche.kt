package de.dokumentenpilot.app

object AssistentSuche {
    fun dokumenteNachTypFinden(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        dokumentTyp: String
    ): List<Pair<String, String>> {
        return dokumente.filter { dokument ->
            val dateiname = dokument.second
            val ocrText = ocrTexte[dokument.first] ?: ""
            DokumentTypErkennung.erkennen(
                "$dateiname\n$ocrText"
            ) == dokumentTyp
        }
    }


    fun gehaltsabrechnungenFinden(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>
    ): List<Pair<String, String>> {
        return dokumente.filter { dokument ->
            UnterlagenSuche.istGehaltsabrechnung(
                dokument.second,
                ocrTexte[dokument.first] ?: ""
            )
        }.sortedWith(
            compareByDescending<Pair<String, String>> { dokument ->
                AssistentDatum.abrechnungsmonat(
                    dokument.second,
                    ocrTexte[dokument.first] ?: ""
                ) ?: 0
            }
        )
    }

    fun freieSuche(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        suchanfrage: String
    ): List<Pair<String, String>> {
        val ignorieren = setOf(
            "zeige", "zeig", "mir", "meine", "meinen", "mein",
            "suche", "finde", "bitte", "die", "der", "das",
            "den", "dem", "von", "vom", "aus", "alle", "letzten",
            "letzte", "dokumente", "unterlagen", "dokument"
        )

        val woerter = suchanfrage.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in ignorieren }

        if (woerter.isEmpty()) return emptyList()

        return dokumente.mapNotNull { dokument ->
            val inhalt = (
                dokument.second + " " +
                (ocrTexte[dokument.first] ?: "")
            ).lowercase()

            val punkte = woerter.count { inhalt.contains(it) }
            if (punkte > 0) dokument to punkte else null
        }.sortedByDescending { it.second }
            .map { it.first }
    }

}
