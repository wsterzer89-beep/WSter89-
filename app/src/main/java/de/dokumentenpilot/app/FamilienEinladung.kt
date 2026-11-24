package de.dokumentenpilot.app

/** Local simulation only. No authentication, transport or production access. */
internal object FamilienEinladung {
    enum class Status { OFFEN, ANGENOMMEN, WIDERRUFEN }
    data class Einladung(
        val id: String,
        val eigentuemer: String,
        val empfaenger: String,
        val gueltigBisMillis: Long,
        val status: Status = Status.OFFEN
    )

    fun annehmen(einladung: Einladung, mitglied: String, jetztMillis: Long): Einladung? {
        if (einladung.id.isBlank() || einladung.eigentuemer.isBlank() ||
            mitglied.isBlank() || mitglied == einladung.eigentuemer ||
            mitglied != einladung.empfaenger || jetztMillis >= einladung.gueltigBisMillis ||
            einladung.status != Status.OFFEN) return null
        return einladung.copy(status = Status.ANGENOMMEN)
    }

    fun widerrufen(einladung: Einladung, mitglied: String): Einladung? {
        if (mitglied.isBlank() || mitglied != einladung.eigentuemer) return null
        return einladung.copy(status = Status.WIDERRUFEN)
    }
}
