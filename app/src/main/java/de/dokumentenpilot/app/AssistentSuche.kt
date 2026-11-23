package de.dokumentenpilot.app

object AssistentSuche {

    fun unterlagenZusammenstellen(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        anforderungen: List<UnterlagenAnforderung>
    ): List<Pair<String, String>> {
        val ergebnis = mutableListOf<Pair<String, String>>()
        val verwendeteIds = mutableSetOf<String>()

        for (anforderung in anforderungen) {
            val istMonatsdokument =
                anforderung.dokumentenart == "Gehaltsabrechnungen" ||
                anforderung.dokumentenart == "Kontoauszüge"
            val kandidaten = dokumente.filter { dokument ->
                val ocr = ocrTexte[dokument.first] ?: ""
                val text = dokument.second + " " + ocr

                val passtZurArt =
                    UnterlagenErkennung.passtZuUnterlage(
                        anforderung.dokumentenart, text
                    )

                val passtZumJahr = if (anforderung.jahr == null) {
                    true
                } else if (
                    !istMonatsdokument &&
                    anforderung.dokumentenart != "Steuerbescheid" &&
                   anforderung.dokumentenart != "Steuererklärung"
                ) {
                    true
                } else if (
                    (anforderung.dokumentenart == "Steuerbescheid" ||
                                               anforderung.dokumentenart == "Steuererklärung")
                ) {
                    val steuerjahr = Regex(
                        """(?i)(?:veranlagungszeitraum|steuerjahr|(?:einkommen)?steuererkl(?:ä|ae)rung|einkommensteuerbescheid|steuerbescheid|für\s+(?:das\s+jahr\s+)?|einkommensteuer\s+für)\s*[:-]?\s*(20\d{2})"""
                    ).find(text)?.groupValues?.get(1)?.toIntOrNull()

                    steuerjahr == anforderung.jahr
                } else {
                    val monat = if (
                        anforderung.dokumentenart == "Gehaltsabrechnungen"
                    ) {
                        AssistentDatum.abrechnungsmonat(
                            dokument.second, ocr
                        )
                    } else {
                        AssistentDatum.kontoauszugsmonat(
                            dokument.second, ocr
                        )
                    }

                    monat != null &&
                        monat / 100 == anforderung.jahr
                }

                passtZurArt && passtZumJahr
            }

            val sortiert = if (anforderung.neuesteZuerst && istMonatsdokument) {
                kandidaten.sortedWith(
                    compareByDescending<Pair<String, String>> { dokument ->
                        val ocr = ocrTexte[dokument.first] ?: ""
                        if (anforderung.dokumentenart == "Gehaltsabrechnungen") {
                            AssistentDatum.abrechnungsmonat(
                                dokument.second, ocr
                            ) ?: 0
                        } else {
                            AssistentDatum.kontoauszugsmonat(
                                dokument.second, ocr
                            ) ?: 0
                        }
                    }
                )
            } else {
                kandidaten
            }

            val ausgewaehlt = if (anforderung.neuesteZuerst && istMonatsdokument) {
                val monate = mutableSetOf<Int>()

                sortiert.filter { dokument ->
                    val ocr = ocrTexte[dokument.first] ?: ""

                    val monat = if (
                        anforderung.dokumentenart == "Gehaltsabrechnungen"
                    ) {
                        AssistentDatum.abrechnungsmonat(
                            dokument.second, ocr
                        )
                    } else {
                        AssistentDatum.kontoauszugsmonat(
                            dokument.second, ocr
                        )
                    }

                    monat != null && monate.add(monat)
                }
            } else {
                sortiert
            }

            val begrenzt = anforderung.anzahl?.let {
                ausgewaehlt.take(it)
            } ?: if (istMonatsdokument) {
                ausgewaehlt
            } else {
                ausgewaehlt.take(1)
            }

            for (dokument in begrenzt) {
                if (verwendeteIds.add(dokument.first)) {
                    ergebnis.add(dokument)
                }
            }
        }

        return ergebnis
    }


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
