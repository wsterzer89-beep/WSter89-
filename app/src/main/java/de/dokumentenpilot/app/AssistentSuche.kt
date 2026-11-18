package de.dokumentenpilot.app

object AssistentSuche {

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
}
