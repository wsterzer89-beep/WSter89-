package de.dokumentenpilot.app

internal class FamilienArchivNavigation {
    enum class Bereich { ARCHIV, MAPPEN, KATEGORIEN, ALLE, MAPPE, KATEGORIE, ZULETZT }
    data class Seite(val bereich: Bereich, val name: String = "")

    private val stapel = mutableListOf(Seite(Bereich.ARCHIV))
    private val scrollpositionen = mutableMapOf<Seite, Int>()
    var titelAufsteigend = true
    val seite get() = stapel.last()
    val scrollposition get() = scrollpositionen[seite] ?: 0

    fun oeffnen(ziel: Seite, scroll: Int) {
        scrollpositionen[seite] = scroll
        stapel.add(ziel)
    }

    fun merken(scroll: Int) { scrollpositionen[seite] = scroll }

    fun zurueck(): Boolean {
        if (stapel.size == 1) return false
        stapel.removeAt(stapel.lastIndex)
        return true
    }

    companion object {
        fun gruppieren(eintraege: List<FamilienArchivAustausch.Eintrag>, mappen: Boolean): Map<String, List<FamilienArchivAustausch.Eintrag>> =
            eintraege.distinctBy { it.id }.flatMap { eintrag ->
                val namen = if (mappen) eintrag.mappen else listOf(eintrag.quellKategorie).filter { eintrag.kategorieSichtbar }
                namen.filter { it.isNotBlank() }.distinct().map { it to eintrag }
            }.groupBy({ it.first }, { it.second }).toSortedMap()

        fun aktionen(eintrag: FamilienArchivAustausch.Eintrag): List<String> = buildList {
            add("Öffnen")
            if (eintrag.umbenennbar) add("Umbenennen")
            if (eintrag.zielKategorien.isNotEmpty()) add("Verschieben")
            add("Dokumenteninformationen")
            if (eintrag.loeschbar) add("Löschung anfragen")
        }

        fun geraetenamen(namen: List<String?>): List<String> {
            val basis = namen.map { it ?: "Familiengerät" }
            val gesamt = basis.groupingBy { it }.eachCount()
            val position = mutableMapOf<String, Int>()
            return basis.map { name ->
                if (gesamt.getValue(name) == 1) name
                else "$name · Gerät ${(position[name] ?: 0).plus(1).also { position[name] = it }}"
            }
        }
    }
}
