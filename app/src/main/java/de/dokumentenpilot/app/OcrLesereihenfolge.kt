package de.dokumentenpilot.app

internal object OcrLesereihenfolge {
    data class Block(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun sortieren(bloecke: List<Block>, breite: Int, rechtsNachLinks: Boolean = false): List<Block>? {
        if (breite <= 0 || bloecke.any { it.left >= it.right || it.top >= it.bottom }) return null
        val mitte = breite / 2
        sortierenBei(bloecke, mitte, rechtsNachLinks)?.let { return it }
        val kandidaten = bloecke.flatMap { l -> bloecke.mapNotNull { r ->
            if (r.left - l.right >= maxOf(2, breite / 50)) l.right + (r.left - l.right) / 2 else null
        } }.distinct().filter { it in (breite / 5)..(breite - breite / 5) }
        val varianten = kandidaten.mapNotNull { sortierenBei(bloecke, it, rechtsNachLinks) }.distinct()
        return varianten.singleOrNull()
    }

    private fun sortierenBei(bloecke: List<Block>, mitte: Int, rechtsNachLinks: Boolean): List<Block>? {
        val links = bloecke.filter { it.right <= mitte }
        val rechts = bloecke.filter { it.left >= mitte }
        val quer = bloecke.filter { it !in links && it !in rechts }
        if (links.size < 2 || rechts.size < 2) return null
        if (links.none { l -> rechts.any { r -> l.top < r.bottom && r.top < l.bottom } }) return null
        if (quer.any { q -> (links + rechts).any { q.bottom > it.top } }) return null
        val spalten = if (rechtsNachLinks) listOf(rechts, links) else listOf(links, rechts)
        return quer.sortedBy { it.top } + spalten.flatMap { it.sortedBy { block -> block.top } }
    }
}
