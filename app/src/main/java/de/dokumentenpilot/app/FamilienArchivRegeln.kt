package de.dokumentenpilot.app

/** Validates the scope and permissions of an archive grant before it is saved. */
internal object FamilienArchivRegeln {
    fun validieren(owner: String, peer: String, regel: FamilienBerechtigungen.Freigabe,
                   katalog: List<FamilienBerechtigungen.Dokument>): Boolean {
        if (owner.isBlank() || peer.isBlank() || owner == peer ||
            regel.mitgliedId != peer || regel.archivEigentuemerId != owner ||
            regel.widerrufen || regel.erlaubteAktionen.isEmpty()) return false
        if (!regel.erlaubteAktionen.all {
                it == FamilienBerechtigungen.Aktion.ANSEHEN || it == FamilienBerechtigungen.Aktion.SUCHEN || it == FamilienBerechtigungen.Aktion.ASSISTENT
            }) return false
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
            (regel.neueDokumenteAutomatisch || regel.dokumentIdsBeiFreigabe.isNotEmpty())
    }
}
