package de.dokumentenpilot.app

/** Checks local archive rights before decrypting and again before returning plaintext. */
internal class FamilienArchivZugriff(
    private val eigentuemer: String,
    private val bestaetigt: (String) -> Boolean,
    private val aktiv: () -> Boolean,
    private val katalog: () -> List<FamilienBerechtigungen.Dokument>,
    private val freigaben: () -> List<FamilienBerechtigungen.Freigabe>,
    private val privat: () -> Set<String>,
    private val lesen: (String) -> ByteArray
) {
    private fun erlaubt(peer: String, doc: FamilienBerechtigungen.Dokument, aktion: FamilienBerechtigungen.Aktion): Boolean =
        aktiv() && peer != eigentuemer && bestaetigt(peer) &&
            doc.eigentuemerId == eigentuemer && doc.id !in privat() &&
            FamilienBerechtigungen.erlaubt(peer, doc, aktion, freigaben())

    fun sichtbareDokumente(peer: String): List<FamilienBerechtigungen.Dokument> =
        runCatching {
            if (!aktiv() || !bestaetigt(peer)) return emptyList()
            katalog().filter { erlaubt(peer, it, FamilienBerechtigungen.Aktion.ANSEHEN) &&
                erlaubt(peer, it, FamilienBerechtigungen.Aktion.SUCHEN) }
        }.getOrDefault(emptyList())

    fun darfAnsehen(peer: String, id: String): Boolean = runCatching {
        val doc = katalog().singleOrNull { it.id == id } ?: return false
        erlaubt(peer, doc, FamilienBerechtigungen.Aktion.ANSEHEN)
    }.getOrDefault(false)

    /** Borrowed bytes, cleared after synchronous consumer returns. */
    fun ansehen(peer: String, id: String, empfangen: (ByteArray) -> Unit): Boolean =
        runCatching {
            val doc = katalog().singleOrNull { it.id == id } ?: return false
            if (!erlaubt(peer, doc, FamilienBerechtigungen.Aktion.ANSEHEN)) return false
            val bytes = lesen(doc.id)
            try {
                val aktuell = katalog().singleOrNull { it.id == id } ?: return false
                if (!erlaubt(peer, aktuell, FamilienBerechtigungen.Aktion.ANSEHEN)) return false
                empfangen(bytes)
                true
            } finally { bytes.fill(0) }
        }.getOrDefault(false)
}
