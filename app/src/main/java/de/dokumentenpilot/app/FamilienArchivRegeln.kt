package de.dokumentenpilot.app

/** Validates the scope and permissions of an archive grant before it is saved. */
internal object FamilienArchivRegeln {
    val aktionen = setOf(
        FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN,
        FamilienBerechtigungen.Aktion.ASSISTENT, FamilienBerechtigungen.Aktion.UMBENENNEN,
        FamilienBerechtigungen.Aktion.VERSCHIEBEN, FamilienBerechtigungen.Aktion.LOESCHEN, FamilienBerechtigungen.Aktion.IMPORTIEREN
    )

    fun darfEinreichen(owner: String, peer: String, ziel: String,
                       regeln: List<FamilienBerechtigungen.Freigabe>): Boolean {
        if (owner.isBlank() || peer.isBlank() || owner == peer || ziel.isBlank()) return false
        return regeln.any { regel ->
            regel.archivEigentuemerId == owner && regel.mitgliedId == peer && !regel.widerrufen &&
                FamilienBerechtigungen.Aktion.IMPORTIEREN in regel.erlaubteAktionen &&
                (regel.bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV && regel.bereichId.isEmpty() ||
                    regel.bereich == FamilienBerechtigungen.Bereich.KATEGORIE && regel.bereichId == ziel)
        }
    }

    fun darfVerschieben(
        peer: String, doc: FamilienBerechtigungen.Dokument, ziel: String,
        regeln: List<FamilienBerechtigungen.Freigabe>
    ): Boolean {
        fun erlaubt(eintrag: FamilienBerechtigungen.Dokument, freigaben: List<FamilienBerechtigungen.Freigabe>) =
            FamilienBerechtigungen.erlaubt(peer, eintrag, FamilienBerechtigungen.Aktion.ANSEHEN, freigaben) &&
                FamilienBerechtigungen.erlaubt(peer, eintrag, FamilienBerechtigungen.Aktion.VERSCHIEBEN, freigaben)
        if (ziel == doc.kategorie || !erlaubt(doc, regeln)) return false
        val zielregeln = regeln.filter {
            it.bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV ||
                it.bereich == FamilienBerechtigungen.Bereich.KATEGORIE
        }
        return erlaubt(doc.copy(kategorie = ziel), zielregeln)
    }

    fun validieren(owner: String, peer: String, regel: FamilienBerechtigungen.Freigabe,
                   katalog: List<FamilienBerechtigungen.Dokument>): Boolean {
        if (owner.isBlank() || peer.isBlank() || owner == peer ||
            regel.mitgliedId != peer || regel.archivEigentuemerId != owner ||
            regel.widerrufen || regel.erlaubteAktionen.isEmpty()) return false
        if (!aktionen.containsAll(regel.erlaubteAktionen)) return false
        val import = FamilienBerechtigungen.Aktion.IMPORTIEREN in regel.erlaubteAktionen
        if (import && regel.bereich != FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV &&
            regel.bereich != FamilienBerechtigungen.Bereich.KATEGORIE) return false
        val eigene = katalog.filter { it.eigentuemerId == owner }
        if (eigene.any { it.id.isBlank() } || eigene.map { it.id }.toSet().size != eigene.size) return false
        val passend = eigene.filter {
            when (regel.bereich) {
                FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV -> regel.bereichId.isEmpty()
                FamilienBerechtigungen.Bereich.KATEGORIE -> regel.bereichId.isNotBlank() && it.kategorie == regel.bereichId
                FamilienBerechtigungen.Bereich.MAPPE -> regel.bereichId.isNotBlank() && regel.bereichId in it.mappenIds
                FamilienBerechtigungen.Bereich.DOKUMENT -> it.id == regel.bereichId
            }
        }
        if (regel.bereich == FamilienBerechtigungen.Bereich.DOKUMENT)
            return passend.size == 1 && !regel.neueDokumenteAutomatisch && regel.dokumentIdsBeiFreigabe.isEmpty()
        if (regel.bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV && regel.bereichId.isNotEmpty()) return false
        if (regel.bereich != FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV && regel.bereichId.isBlank()) return false
        return regel.dokumentIdsBeiFreigabe.all { id -> passend.any { it.id == id } } &&
            (import || regel.neueDokumenteAutomatisch || regel.dokumentIdsBeiFreigabe.isNotEmpty())
    }
}
