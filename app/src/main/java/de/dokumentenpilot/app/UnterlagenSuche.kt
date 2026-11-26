package de.dokumentenpilot.app

object UnterlagenSuche {

    private val anforderungsTitel = Regex(
        """\b(?:anforderungsschreiben|unterlagenanforderung|checkliste|(?:angeforderte|benötigte|benoetigte|einzureichende)\s+unterlagen|unterlagen\s+nachreichen)\b"""
    )
    private val anforderungsText = Regex(
        """\b(?:folgende\s+unterlagen|einzureichen|(?:reichen|senden|schicken|legen|übermitteln|uebermitteln)\s+sie|bitte\s+(?:um|nachreichen)|(?:benötig|benoetig)(?:en|t)\s+(?:wir|sie))\b"""
    )
    private val gehaltsbegriffe = Regex(
        """(?<!\p{L})(?:${UnterlagenErkennung.gehaltsbegriffe.joinToString("|")})(?!\p{L})"""
    )
    private val anforderungsFormulierung = Regex(
        """${gehaltsbegriffe.pattern}\s+(?:(?:aus\s+)?(?:den|der)\s+letzten\s+|(?:einzureichen|nachzureichen)\b)"""
    )
    private val satzende = Regex("""[.!?](?:\s|$)""")
    private val geldbetrag = """(?<!\d)(?:\d{1,3}(?:[. ]\d{3})+|\d+)[,.]\d{2}(?!\d)"""
    private val brutto = Regex(
        """\b(?:gesamt[ -]?brutto|steuer[ -]?brutto|brutto(?:[ -]?(?:gehalt|verdienst|bezüge))?)\s*[:=]?\s*(?:eur\s*)?$geldbetrag"""
    )
    private val netto = Regex(
        """\b(?:netto(?:[ -]?(?:verdienst|gehalt|auszahlung|bezug|entgelt))?|auszahlungs[ -]?betrag)\s*[:=]?\s*(?:eur\s*)?$geldbetrag"""
    )

    private val lohnMerkmal = Regex(
        """\b(?:steuer[ -]?brutto|brutto[ -]?(?:gehalt|verdienst|bezüge)|netto[ -]?(?:gehalt|verdienst|auszahlung|bezug|entgelt))\b"""
    )

    fun istAnforderungsschreiben(dokumentName: String, erkannterText: String): Boolean {
        val name = UnterlagenErkennung.ocrTextNormalisieren(dokumentName).replace('_', ' ')
        val text = UnterlagenErkennung.ocrTextNormalisieren(erkannterText)
        if (anforderungsTitel.containsMatchIn(name)) return true
        // Ein expliziter Dokumenttitel ist stärker als beiläufige Aufforderungen im Formular.
        val titel = SynonymKatalog.normalisieren(dokumentName.substringBeforeLast(".pdf", dokumentName))
        if (SynonymKatalog.familien.values.any { aliases ->
                aliases.any { SynonymKatalog.normalisieren(it) == titel }
            }) return false
        if (text.lineSequence().take(6).any { anforderungsTitel.containsMatchIn(it) }) return true
        if (anforderungsFormulierung.containsMatchIn(text) ||
            (name.startsWith("anforderung ") && SynonymKatalog.erkannteArten(name).isNotEmpty())
        ) return true

        return anforderungsText.findAll(text).any { treffer ->
            val rest = text.substring(treffer.range.first).take(280)
            val ende = satzende.find(rest)?.range?.first ?: rest.length
            val abschnitt = rest.take(ende)
            UnterlagenErkennung.angeforderteUnterlagen(abschnitt).isNotEmpty() ||
                treffer.value.matches(Regex("""folgende\s+unterlagen"""))
        }
    }

    fun istGehaltsabrechnung(dokumentName: String, erkannterText: String): Boolean {
        if (istAnforderungsschreiben(dokumentName, erkannterText)) return false

        val text = UnterlagenErkennung.ocrTextNormalisieren(erkannterText)
        val name = UnterlagenErkennung.ocrTextNormalisieren(dokumentName)
        if (SynonymKatalog.passt("Gehaltsabrechnungen", name) ||
            SynonymKatalog.passt("Gehaltsabrechnungen", text)) {
            return true
        }

        val hatBrutto = brutto.containsMatchIn(text)
        val hatNetto = netto.containsMatchIn(text)
        val hatAbrechnungsmonat = AssistentDatum.abrechnungsmonat("", text) != null
        return (hatAbrechnungsmonat && (hatBrutto || hatNetto)) ||
            (hatBrutto && hatNetto && lohnMerkmal.containsMatchIn(text))
    }
}
