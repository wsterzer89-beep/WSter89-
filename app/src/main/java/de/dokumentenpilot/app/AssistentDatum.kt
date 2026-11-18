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

    fun abrechnungsmonat(
        dateiname: String,
        ocrText: String
    ): Int? {
        val text = "$dateiname\n$ocrText".lowercase()

        // Vorrang: ausdrücklich genannter Abrechnungsmonat im PDF
        val monatsAngabe = Regex(
            """(?:abrechnungsmonat|abrechnungszeitraum|entgeltabrechnung\s+für|lohnabrechnung\s+für|gehaltsabrechnung\s+für)\s*[:\-]?\s*([^\n\r]{0,60})""",
            RegexOption.IGNORE_CASE
        )

        for (treffer in monatsAngabe.findAll(ocrText)) {
            val zeile = treffer.groupValues[1]
            val erkannt = datumAusText(zeile)
            if (erkannt != null) return erkannt
        }

        val muster = listOf(
            Regex("""\b(20\d{2})[-./_](0?[1-9]|1[0-2])\b"""),
            Regex("""\b(0?[1-9]|1[0-2])[-./_](20\d{2})\b""")
        )

        muster[0].find(text)?.let {
            val jahr = it.groupValues[1].toInt()
            val monat = it.groupValues[2].toInt()
            return jahr * 100 + monat
        }

        muster[1].find(text)?.let {
            val monat = it.groupValues[1].toInt()
            val jahr = it.groupValues[2].toInt()
            return jahr * 100 + monat
        }

        for ((name, monat) in monate) {
            val regex = Regex(
                """\b${Regex.escape(name)}\s+(20\d{2})\b"""
            )
            regex.find(text)?.let {
                return it.groupValues[1].toInt() * 100 + monat
            }
        }

        return null
    }
}
