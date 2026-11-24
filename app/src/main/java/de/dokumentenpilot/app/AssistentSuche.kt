package de.dokumentenpilot.app

object AssistentSuche {

    private fun monatsSchluessel(
        art: String,
        dateiname: String,
        ocr: String
    ): String? {
        val monat = when (art) {
            "Gehaltsabrechnungen" -> AssistentDatum.abrechnungsmonat(dateiname, ocr)
            "Kontoauszüge" -> AssistentDatum.kontoauszugsmonat(dateiname, ocr)
            else -> null
        } ?: return null
        if (art != "Kontoauszüge") return monat.toString()

        // Zwei Konten mit Auszügen desselben Monats sind nicht identisch.
        // Ohne Kontenmetadaten bewusst konservativ auf Monatsbasis deduplizieren.
        val iban = Regex("""(?i)\bDE\d{2}(?:[\s-]?\d{4}){5}\b""")
            .find(ocr)?.value?.replace(Regex("""[^\p{L}\d]"""), "")?.uppercase()
        val auszug = Regex(
            """(?i)\b(?:auszugsnummer|auszug\s*(?:nr\.?|nummer))\s*[:#-]?\s*(\d{1,5})\b"""
        ).find(ocr)?.groupValues?.get(1)
        return "${monat}|${iban ?: "unbekannt"}|${auszug ?: "unbekannt"}"
    }

    fun unterlagenZusammenstellen(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        anforderungen: List<UnterlagenAnforderung>
    ): List<Pair<String, String>> {
        val ergebnis = mutableListOf<Pair<String, String>>()
        val verwendeteIds = mutableSetOf<String>()

        for (anforderung in anforderungen) {
            val istGehaltsabrechnung = anforderung.dokumentenart == "Gehaltsabrechnungen"
            val neuesteZuerst = anforderung.neuesteZuerst || istGehaltsabrechnung
            val istMonatsdokument = istGehaltsabrechnung ||
                anforderung.dokumentenart == "Kontoauszüge"
            val kandidaten = dokumente.distinctBy { it.first }.filter { dokument ->
                val ocr = ocrTexte[dokument.first] ?: ""
                val text = dokument.second + " " + ocr

                val passtZurArt = passtZurAnforderung(
                    anforderung.dokumentenart, dokument.second, ocr
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

            fun monatVon(dokument: Pair<String, String>): Int? {
                val ocr = ocrTexte[dokument.first] ?: ""
                return when (anforderung.dokumentenart) {
                    "Gehaltsabrechnungen" -> AssistentDatum.abrechnungsmonat(dokument.second, ocr)
                    "Kontoauszüge" -> AssistentDatum.kontoauszugsmonat(dokument.second, ocr)
                    else -> AssistentDatum.dokumentMonat(dokument.second, ocr)
                }
            }
            val sortiert = if (neuesteZuerst) {
                kandidaten.sortedByDescending { monatVon(it) ?: 0 }
            } else kandidaten

            val ausgewaehlt = if (neuesteZuerst && istMonatsdokument) {
                val monate = mutableSetOf<String>()
                val mitMonat = sortiert.filter { dokument ->
                    val schluessel = monatsSchluessel(
                        anforderung.dokumentenart,
                        dokument.second,
                        ocrTexte[dokument.first].orEmpty()
                    )
                    schluessel != null && monate.add(schluessel)
                }
                // Für Kontoauszüge ohne erkannten Monat bei der bisherigen Vorauswahl bleiben.
                if (mitMonat.isEmpty() && !istGehaltsabrechnung) sortiert else mitMonat
            } else sortiert

            val begrenzt = anforderung.anzahl?.let {
                ausgewaehlt.take(it.coerceAtLeast(0))
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


    fun fehlendeUnterlagen(
        inhalt: List<Pair<String, String>>,
        archiv: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        anforderung: UnterlagenAnforderung
    ): List<Pair<String, String>> {
        val vorhanden = unterlagenZusammenstellen(inhalt, ocrTexte, listOf(anforderung))
        val anzahl = anforderung.anzahl?.minus(vorhanden.size)
        if (anzahl != null && anzahl <= 0) return emptyList()

        val vorhandeneIds = inhalt.map { it.first }.toSet()
        val vorhandeneSchluessel = if (anforderung.neuesteZuerst ||
            anforderung.dokumentenart == "Gehaltsabrechnungen"
        ) vorhanden.mapNotNull { dokument ->
            monatsSchluessel(
                anforderung.dokumentenart,
                dokument.second,
                ocrTexte[dokument.first].orEmpty()
            )
        }.toSet() else emptySet()
        return unterlagenZusammenstellen(
            archiv.filter { dokument ->
                dokument.first !in vorhandeneIds && monatsSchluessel(
                    anforderung.dokumentenart,
                    dokument.second,
                    ocrTexte[dokument.first].orEmpty()
                ) !in vorhandeneSchluessel
            },
            ocrTexte,
            listOf(anforderung.copy(anzahl = anzahl))
        )
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
        return unterlagenZusammenstellen(
            dokumente, ocrTexte,
            listOf(UnterlagenAnforderung("Gehaltsabrechnungen", anzahl, true))
        )
    }

    fun passtZurAnforderung(art: String, name: String, ocr: String): Boolean =
        if (art == "Gehaltsabrechnungen") {
            UnterlagenSuche.istGehaltsabrechnung(name, ocr)
        } else {
            !UnterlagenSuche.istAnforderungsschreiben(name, ocr) &&
                UnterlagenErkennung.passtZuUnterlage(art, "$name $ocr")
        }

    fun ocrSuchePasst(name: String, ocr: String, suche: String): Boolean {
        val anfrage = UnterlagenErkennung.ocrTextNormalisieren(suche)
        if (anfrage.isBlank()) return false
        val arten = SynonymKatalog.erkannteArten(suche)
        if (arten.size == 1 && SynonymKatalog.passt(arten.single(), suche) &&
            SynonymKatalog.synonyme(suche).size > 1
        ) {
            return passtZurAnforderung(arten.single(), name, ocr)
        }
        if (anfrage in UnterlagenErkennung.gehaltsbegriffe.map(SynonymKatalog::normalisieren)) {
            return UnterlagenSuche.istGehaltsabrechnung(name, ocr)
        }
        val leerraum = Regex("""\s+""")
        return listOf(name, ocr).any {
            UnterlagenErkennung.ocrTextNormalisieren(it).replace(leerraum, " ")
                .contains(anfrage.replace(leerraum, " "))
        }
    }

    fun assistentTreffer(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        anfrage: AssistentAnfrage
    ): List<Pair<String, String>> {
        val erkannteAnforderungen = UnterlagenErkennung.anforderungenErkennen(anfrage.beschreibung)
        if (erkannteAnforderungen.isNotEmpty()) {
            // Jede Dokumentenart besitzt ihre eigene Mengen- und Jahresangabe.
            val angepasst = erkannteAnforderungen.map { anforderung ->
                if (erkannteAnforderungen.size == 1 &&
                    anforderung.dokumentenart == anfrage.dokumentTyp
                ) anforderung.copy(
                    anzahl = anforderung.anzahl ?: anfrage.anzahl,
                    neuesteZuerst = anforderung.neuesteZuerst ||
                        Regex("""\b(?:letzte|letzten|neueste|neuesten)\b""")
                            .containsMatchIn(anfrage.beschreibung.lowercase())
                ) else anforderung
            }
            return unterlagenZusammenstellen(dokumente, ocrTexte, angepasst)
        }
        val treffer = freieSuche(dokumente, ocrTexte, anfrage.beschreibung)
        return anfrage.anzahl?.let { treffer.take(it.coerceAtLeast(0)) } ?: treffer
    }

    fun freieSuche(
        dokumente: List<Pair<String, String>>,
        ocrTexte: Map<String, String>,
        suchanfrage: String
    ): List<Pair<String, String>> {
        val ignorieren = setOf(
            "zeige", "zeig", "mir", "meine", "meinen", "mein",
            "suche", "finde", "bitte", "die", "der", "das",
            "den", "dem", "von", "vom", "aus", "für", "alle", "letzten", "neueste", "neuesten",
            "letzte", "dokumente", "unterlagen", "dokument"
        )

        val woerter = UnterlagenErkennung.ocrTextNormalisieren(suchanfrage)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in ignorieren }

        if (woerter.isEmpty()) return emptyList()

        val gehaltsbegriffe = UnterlagenErkennung.gehaltsbegriffe
        val suchtGehaltsabrechnungen = woerter.any { it in gehaltsbegriffe }

        if (suchtGehaltsabrechnungen) {
            val menge = UnterlagenErkennung.anforderungenErkennen(suchanfrage)
                .firstOrNull { it.dokumentenart == "Gehaltsabrechnungen" }
                ?: return emptyList()
            val restlicheWoerter = woerter.filterNot { wort ->
                wort in gehaltsbegriffe ||
                    (menge.anzahl != null && (
                        UnterlagenErkennung.zahlen[wort] == menge.anzahl ||
                            wort in setOf("monate", "monaten", "monat")
                    ))
            }
            return dokumente.filter { dokument ->
                val name = dokument.second
                val ocrText = ocrTexte[dokument.first] ?: ""
                val inhalt = UnterlagenErkennung.ocrTextNormalisieren("$name $ocrText")

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
