package de.dokumentenpilot.app

internal object MonatsZeitraum {
    private val monate = listOf("januar", "februar", "maerz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember")
    private val numerisch = Regex("""\b(?:von\s+)?(0?[1-9]|1[0-2])[./](20\d{2})\s*(?:bis|[-–])\s*(0?[1-9]|1[0-2])[./](20\d{2})\b""")
    private val name = monate.joinToString("|")
    private val ausgeschrieben = Regex("""\b($name)(?:\s+(20\d{2}))?\s*(?:bis|[-–])\s*($name)\s+(20\d{2})\b""")
    fun erkennen(text: String): IntRange? {
        val normal = SynonymKatalog.normalisieren(text)
        numerisch.find(normal)?.let {
            val (m1, j1, m2, j2) = it.destructured
            return (j1.toInt() * 100 + m1.toInt())..(j2.toInt() * 100 + m2.toInt())
        }
        ausgeschrieben.find(normal)?.let {
            val (m1, j1, m2, j2) = it.destructured
            return ((j1.toIntOrNull() ?: j2.toInt()) * 100 + monate.indexOf(m1) + 1)..
                (j2.toInt() * 100 + monate.indexOf(m2) + 1)
        }
        return null
    }
}
