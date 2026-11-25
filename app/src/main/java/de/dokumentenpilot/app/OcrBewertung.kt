package de.dokumentenpilot.app

internal object OcrBewertung {
    fun bewertung(text: String, modell: String, konfidenz: Int): Int {
        if (konfidenz !in 50..100) return 0
        val schrift = when (modell) {
            "ara" -> Character.UnicodeScript.ARABIC
            "rus", "ukr" -> Character.UnicodeScript.CYRILLIC
            else -> return 0
        }
        return text.count { it.isLetter() && Character.UnicodeScript.of(it.code) == schrift } * konfidenz / 100
    }
}
