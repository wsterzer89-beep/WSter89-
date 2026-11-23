package de.dokumentenpilot.app

object AssistentDatum {

    private val monate = mapOf(
        "januar" to 1, "jan" to 1,
        "februar" to 2, "feb" to 2,
        "märz" to 3, "maerz" to 3, "mrz" to 3,
        "april" to 4, "apr" to 4,
        "mai" to 5,
        "juni" to 6, "jun" to 6,
        "juli" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9,
        "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "dezember" to 12, "dez" to 12
    )

    private fun datumAusText(text: String): Int? {
        val monate = mapOf(
            "januar" to 1, "jan" to 1,
            "februar" to 2, "feb" to 2,
            "märz" to 3, "maerz" to 3, "mär" to 3,
            "april" to 4, "apr" to 4,
            "mai" to 5, "juni" to 6, "jun" to 6,
            "juli" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9,
            "oktober" to 10, "okt" to 10,
            "november" to 11, "nov" to 11,
            "dezember" to 12, "dez" to 12
        )
        val klein = text.lowercase()

        val vollesDatum = Regex(
            """\b(0?[1-9]|[12]\d|3[01])[./-](0?[1-9]|1[0-2])[./-](20\d{2})\b"""
        )
        vollesDatum.find(klein)?.let {
            return it.groupValues[3].toInt() * 100 +
                it.groupValues[2].toInt()
        }


        val jahrMonat = Regex("""\b(20\d{2})[-./_](0?[1-9]|1[0-2])\b""")
        jahrMonat.find(klein)?.let {
            return it.groupValues[1].toInt() * 100 +
                it.groupValues[2].toInt()
        }

        val monatJahr = Regex("""\b(0?[1-9]|1[0-2])[-./_](20\d{2})\b""")
        monatJahr.find(klein)?.let {
            return it.groupValues[2].toInt() * 100 +
                it.groupValues[1].toInt()
        }

        for ((name, monat) in monate) {
            val regex = Regex("""\b${Regex.escape(name)}\s+(20\d{2})\b""")
            regex.find(klein)?.let {
                return it.groupValues[1].toInt() * 100 + monat
            }
        }
        return null
    }

    fun dokumentMonat(
        dateiname: String,
        ocrText: String
    ): Int? {
        val kennzeichnungen = Regex(
            """(?:abrechnungsmonat|abrechnungszeitraum|abrechnungsperiode|auszugsdatum|auszugszeitraum|kontoauszug\s+(?:für|vom)|rechnungsdatum|belegdatum|datum)\s*[:-]?\s*([^\n\r]{0,60})""",
            RegexOption.IGNORE_CASE
        )

        for (treffer in kennzeichnungen.findAll(ocrText)) {
            val erkannt = datumAusText(treffer.groupValues[1])
            if (erkannt != null) return erkannt
        }

        return datumAusText(dateiname)
    }

    fun abrechnungsmonat(
        dateiname: String,
        ocrText: String
    ): Int? {
        val kennzeichnungen = Regex(
            """(?:abrechnungsmonat|abrechnungszeitraum|abrechnungsperiode|entgeltabrechnung\s+für|lohnabrechnung\s+für|gehaltsabrechnung\s+für)\s*[:-]?\s*([^\n\r]{0,60})""",
            RegexOption.IGNORE_CASE
        )

        for (treffer in kennzeichnungen.findAll(ocrText)) {
            val erkannt = datumAusText(treffer.groupValues[1])
            if (erkannt != null) return erkannt
        }

        return datumAusText(dateiname)
    }

    fun kontoauszugsmonat(
        dateiname: String,
        ocrText: String
    ): Int? {
        val zeitraum = Regex(
            """(?i)(?:auszugszeitraum|kontoauszug\s+für|kontoauszug\s+vom|kontoauszug\s+monat|auszugsmonat)\s*[:-]?\s*([^\n\r]{0,80})"""
        )

        for (treffer in zeitraum.findAll(ocrText)) {
            val erkannt = datumAusText(treffer.groupValues[1])
            if (erkannt != null) return erkannt
        }

        val auszugImDateinamen = datumAusText(dateiname)
        if (auszugImDateinamen != null) return auszugImDateinamen

        val auszugsdatum = Regex(
            """(?i)\bauszugsdatum\s*[:-]?\s*([^\n\r]{0,60})"""
        )

        for (treffer in auszugsdatum.findAll(ocrText)) {
            val erkannt = datumAusText(treffer.groupValues[1])
            if (erkannt != null) return erkannt
        }

        return null
    }
}
