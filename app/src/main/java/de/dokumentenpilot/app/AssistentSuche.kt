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

    fun neuesteUnterschiedlicheAbrechnungen(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        anzahl: Int
    ): List<Pair<String, String>> {
        val monate = mutableSetOf<Int>()

        return dokumente
            .filter { dokument ->
                UnterlagenSuche.istGehaltsabrechnung(
                    dokument.second,
                    ocrTexte[dokument.first] ?: ""
                )
            }
            .mapNotNull { dokument ->
                val monat = AssistentDatum.abrechnungsmonat(
                    dokument.second,
                    ocrTexte[dokument.first] ?: ""
                )
                if (monat != null) dokument to monat else null
            }
            .sortedByDescending { it.second }
            .filter { (_, monat) -> monate.add(monat) }
            .take(anzahl)
            .map { it.first }
    }

    fun freieSuche(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        suchanfrage: String
    ): List<Pair<String, String>> {
        val ignorieren = setOf(
            "zeige", "zeig", "mir", "meine", "meinen", "mein",
            "suche", "finde", "bitte", "die", "der", "das",
            "den", "dem", "von", "vom", "aus", "für", "alle", "letzten",
            "letzte", "dokumente", "unterlagen", "dokument"
        )

        val woerter = suchanfrage.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in ignorieren }

        if (woerter.isEmpty()) return emptyList()

        val gehaltsbegriffe = listOf(
            "gehaltsabrechnung", "gehaltsabrechnungen",
            "lohnabrechnung", "lohnabrechnungen",
            "entgeltnachweis", "entgeltnachweise",
            "entgeltabrechnung", "entgeltabrechnungen",
            "verdienstnachweis", "verdienstnachweise",
            "gehaltsnachweis", "gehaltsnachweise",
            "lohnzettel"
        )

        val suchtGehaltsabrechnungen = woerter.any { wort ->
            gehaltsbegriffe.any { begriff ->
                wort == begriff
            }
        }

        val restlicheWoerter = if (suchtGehaltsabrechnungen) {
            woerter.filterNot { it in gehaltsbegriffe }
        } else woerter

        if (suchtGehaltsabrechnungen) {
            return dokumente.filter { dokument ->
                val name = dokument.second
                val ocrText = ocrTexte[dokument.first] ?: ""
                val inhalt = "$name $ocrText".lowercase()

                UnterlagenSuche.istGehaltsabrechnung(
                    name, ocrText
                ) && restlicheWoerter.all { wort ->
                    inhalt.contains(wort)
                }
            }.sortedByDescending { dokument ->
                AssistentDatum.abrechnungsmonat(
                    dokument.second,
                    ocrTexte[dokument.first] ?: ""
                ) ?: 0
            }
        }

        val gesuchteTypen =
            DokumentTypErkennung.passendeTypen(suchanfrage)

        val allgemeineTypen = gesuchteTypen.filterNot {
            it == "RECHNUNG" || it == "VERTRAG" ||
                it == "BESCHEID"
        }.toSet()

        if (allgemeineTypen.isNotEmpty()) {
            val typTreffer = dokumente.filter { dokument ->
                val inhalt = (
                    dokument.second + " " +
                    (ocrTexte[dokument.first] ?: "")
                ).lowercase()

                val passendeTypen =
                    DokumentTypErkennung.passendeTypen(inhalt)

                val suchbegriffePassen = woerter.all { wort ->
                    DokumentTypErkennung.suchSynonyme(wort)
                        .any { synonym -> inhalt.contains(synonym) }
                }

                passendeTypen.any { it in allgemeineTypen } &&
                    suchbegriffePassen
            }

            if (typTreffer.isNotEmpty()) {
                return typTreffer
            }
        }

        return dokumente.mapNotNull { dokument ->
            val inhalt = (
                dokument.second + " " +
                (ocrTexte[dokument.first] ?: "")
            ).lowercase()

            val passendeWoerter = woerter.count { wort ->
                    DokumentTypErkennung.suchSynonyme(wort)
                        .any { synonym -> inhalt.contains(synonym) }
                }

            if (passendeWoerter == woerter.size) {
                val dateiname = dokument.second.lowercase()
                val namensTreffer = woerter.count {
                    dateiname.contains(it)
                }
                dokument to (passendeWoerter + namensTreffer * 3)
            } else null
        }.sortedByDescending { it.second }
            .map { it.first }
    }

}
