package de.dokumentenpilot.app

internal object MonatsZeitraum {
    private val muster = Regex("""\b(?:von\s+)?(0?[1-9]|1[0-2])[./](20\d{2})\s*(?:bis|[-–])\s*(0?[1-9]|1[0-2])[./](20\d{2})\b""")
    fun erkennen(text: String): IntRange? {
        val m = muster.find(text) ?: return null
        val (monat1, jahr1, monat2, jahr2) = m.destructured
        val von = jahr1.toInt() * 100 + monat1.toInt()
        val bis = jahr2.toInt() * 100 + monat2.toInt()
        return von..bis
    }
}
