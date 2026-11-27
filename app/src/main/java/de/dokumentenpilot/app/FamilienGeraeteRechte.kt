package de.dokumentenpilot.app

/** Authorization boundary for synthetic pilot documents, not an archive adapter. */
internal object FamilienGeraeteRechte {
    fun erlaubt(
        vertrauen: FamilienGeraeteVertrauen,
        fingerprint: String,
        dokument: FamilienBerechtigungen.Dokument,
        aktion: FamilienBerechtigungen.Aktion,
        freigaben: List<FamilienBerechtigungen.Freigabe>
    ): Boolean {
        if (vertrauen.status(fingerprint) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return false
        if (dokument.eigentuemerId != "demo-owner" || dokument.id !in setOf("demo-1", "demo-2")) return false
        if (aktion !in setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN)) return false
        return FamilienBerechtigungen.erlaubt(
            fingerprint, dokument, aktion,
            freigaben.filter { it.bereich == FamilienBerechtigungen.Bereich.DOKUMENT }
        )
    }
}
