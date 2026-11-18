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

    fun abrechnungsmonat(
        dateiname: String,
        ocrText: String
    ): Int? {
        val text = "$dateiname\n$ocrText".lowercase()

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
