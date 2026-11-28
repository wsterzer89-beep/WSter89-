package de.dokumentenpilot.app

internal enum class FamilienFreigabedauer(val titel: String, val minuten: Long?) {
    AUS("Deaktiviert", 0),
    MINUTEN_30("30 Minuten", 30),
    STUNDE("60 Minuten (1 Stunde)", 60),
    STUNDEN_4("4 Stunden", 240),
    STUNDEN_12("12 Stunden", 720),
    UNBEGRENZT("Bis zur manuellen Deaktivierung", null);

    fun restMillis(start: Long, jetzt: Long): Long? =
        minuten?.let { (it * 60_000 - (jetzt - start)).coerceAtLeast(0) }

    companion object {
        fun ausEinstellung(wert: Any?): FamilienFreigabedauer = when (wert) {
            true -> MINUTEN_30
            is String -> entries.firstOrNull { it.name == wert } ?: AUS
            else -> AUS
        }
    }
}

/** Lives only in the explicitly started service. Settings alone never authorize access. */
internal class FamilienHintergrundLaufzeit(private val jetzt: () -> Long) {
    private data class Freigabe(val start: Long, val dauer: FamilienFreigabedauer)
    private val freigaben = mutableMapOf<String, Freigabe>()

    @Synchronized
    fun aktivieren(peer: String, dauer: FamilienFreigabedauer) {
        if (dauer != FamilienFreigabedauer.AUS && rest(peer) == 0L) {
            freigaben[peer] = Freigabe(jetzt(), dauer)
        }
    }

    @Synchronized
    fun rest(peer: String): Long? {
        val freigabe = freigaben[peer] ?: return 0L
        return freigabe.dauer.restMillis(freigabe.start, jetzt())
    }

    @Synchronized
    fun aendern(peer: String, neu: FamilienFreigabedauer) {
        val alt = freigaben[peer] ?: return
        val zeit = jetzt()
        if (alt.dauer.restMillis(alt.start, zeit) == 0L || neu.restMillis(alt.start, zeit) == 0L) {
            freigaben.remove(peer)
        } else {
            freigaben[peer] = alt.copy(dauer = neu)
        }
    }

    @Synchronized
    fun beenden(peer: String) { freigaben.remove(peer) }

    @Synchronized
    fun alleBeenden() { freigaben.clear() }

    @Synchronized
    fun aktive(erlaubt: (String) -> Boolean): List<String> {
        freigaben.keys.removeAll { !erlaubt(it) || rest(it) == 0L }
        return freigaben.keys.toList()
    }
}
