package de.dokumentenpilot.app

/**
 * Offline testbares Berechtigungsmodell als Vorbereitung für E2EE-Freigaben.
 * WICHTIG: Ohne identitätsprüfenden Server, sichere Gerätebindung und
 * Schlüsselverteilung darf dieses Modell KEINE Familienfreigabe aktivieren.
 */
object FamilienBerechtigungen {
    const val PRODUKTIV_AKTIV = false

    enum class Aktion {
        ANSEHEN, SUCHEN, EXPORTIEREN, HINZUFUEGEN, SCANNEN, IMPORTIEREN,
        BEARBEITEN, UMBENENNEN, VERSCHIEBEN, LOESCHEN, MAPPE_ERSTELLEN,
        MAPPE_BEARBEITEN, ASSISTENT, ZUSAMMENSTELLEN, CHECKLISTE,
        WEITERGEBEN, EINLADEN
    }

    enum class Bereich { GESAMTES_ARCHIV, KATEGORIE, MAPPE, DOKUMENT }

    enum class Vorlage { NUR_ANSEHEN, ANSEHEN_UND_HINZUFUEGEN, MITARBEIT, INDIVIDUELL }

    data class Dokument(
        val id: String,
        val eigentuemerId: String,
        val kategorie: String,
        val mappenIds: Set<String> = emptySet()
    )

    data class Freigabe(
        val mitgliedId: String,
        val bereich: Bereich,
        val bereichId: String,
        val erlaubteAktionen: Set<Aktion>,
        // Snapshot der bei Freigabe vorhandenen Dokumente.
        val dokumentIdsBeiFreigabe: Set<String> = emptySet(),
        val neueDokumenteAutomatisch: Boolean = false,
        val widerrufen: Boolean = false
    )

    fun vorlage(typ: Vorlage): Set<Aktion> = when (typ) {
        Vorlage.NUR_ANSEHEN -> setOf(Aktion.ANSEHEN, Aktion.SUCHEN)
        Vorlage.ANSEHEN_UND_HINZUFUEGEN -> setOf(
            Aktion.ANSEHEN, Aktion.SUCHEN, Aktion.HINZUFUEGEN, Aktion.SCANNEN, Aktion.IMPORTIEREN
        )
        Vorlage.MITARBEIT -> Aktion.entries.toSet() -
            setOf(Aktion.EINLADEN, Aktion.WEITERGEBEN, Aktion.LOESCHEN)
        Vorlage.INDIVIDUELL -> emptySet()
    }

    /**
     * Die Aktion muss in derselben Freigaberegel wie der Dokumentumfang stehen.
     * Mehrere teilweise passende Regeln erzeugen keine Kreuzkombination.
     */
    fun erlaubt(
        mitgliedId: String,
        dokument: Dokument,
        aktion: Aktion,
        freigaben: List<Freigabe>
    ): Boolean {
        if (mitgliedId == dokument.eigentuemerId) return true
        if (mitgliedId.isBlank() || dokument.id.isBlank()) return false
        return freigaben.any { regel ->
            !regel.widerrufen &&
                regel.mitgliedId == mitgliedId &&
                aktion in regel.erlaubteAktionen &&
                when (regel.bereich) {
                    Bereich.GESAMTES_ARCHIV,
                    Bereich.KATEGORIE,
                    Bereich.MAPPE -> {
                        val bereichPasst = when (regel.bereich) {
                            Bereich.GESAMTES_ARCHIV -> true
                            Bereich.KATEGORIE -> regel.bereichId == dokument.kategorie
                            Bereich.MAPPE -> regel.bereichId in dokument.mappenIds
                            else -> false
                        }
                        bereichPasst && (regel.neueDokumenteAutomatisch ||
                            dokument.id in regel.dokumentIdsBeiFreigabe)
                    }
                    Bereich.DOKUMENT -> regel.bereichId == dokument.id
                }
        }
    }

    /** Vor der Suche filtern: niemals erst nach der Ergebnisbildung. */
    fun sichtbareDokumente(
        mitgliedId: String,
        dokumente: List<Dokument>,
        freigaben: List<Freigabe>
    ): List<Dokument> = dokumente.filter {
        erlaubt(mitgliedId, it, Aktion.ANSEHEN, freigaben) &&
            erlaubt(mitgliedId, it, Aktion.SUCHEN, freigaben)
    }
}
