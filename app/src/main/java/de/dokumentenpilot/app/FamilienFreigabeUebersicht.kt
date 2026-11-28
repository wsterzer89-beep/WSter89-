package de.dokumentenpilot.app

import de.dokumentenpilot.app.FamilienBerechtigungen.Aktion
import de.dokumentenpilot.app.FamilienBerechtigungen.Bereich
import de.dokumentenpilot.app.FamilienBerechtigungen.Freigabe

internal object FamilienFreigabeUebersicht {
    data class Zugriff(val eintrag: FamilienArchivDaten.Eintrag, val rechte: Set<Aktion>, val urspruenge: List<Freigabe>)

    fun zugriffe(peer: String, katalog: List<FamilienArchivDaten.Eintrag>, regeln: FamilienArchivFreigaben.Regeln,
                 kategorien: List<String>): List<Zugriff> =
        katalog.distinctBy { it.dokument.id }.mapNotNull { eintrag ->
            val doc = eintrag.dokument
            if (doc.id in regeln.privat || peer == doc.eigentuemerId) return@mapNotNull null
            fun erlaubt(aktion: Aktion) = FamilienBerechtigungen.erlaubt(peer, doc, aktion, regeln.freigaben)
            if (!erlaubt(Aktion.ANSEHEN)) return@mapNotNull null
            val rechte = FamilienArchivRegeln.aktionen.filterTo(mutableSetOf()) {
                when (it) {
                    Aktion.IMPORTIEREN -> false
                    Aktion.VERSCHIEBEN -> kategorien.any { ziel -> FamilienArchivRegeln.darfVerschieben(peer, doc, ziel, regeln.freigaben) }
                    else -> erlaubt(it)
                }
            }
            val urspruenge = regeln.freigaben.filter { regel ->
                rechte.any { FamilienBerechtigungen.erlaubt(peer, doc, it, listOf(regel)) }
            }
            Zugriff(eintrag, rechte, urspruenge)
        }

    fun umfasst(zugriff: Zugriff, bereich: Bereich, id: String): Boolean {
        val doc = zugriff.eintrag.dokument
        return when (bereich) {
            Bereich.DOKUMENT -> doc.id == id
            Bereich.MAPPE -> id in doc.mappenIds
            Bereich.KATEGORIE -> doc.kategorie == id
            Bereich.GESAMTES_ARCHIV -> true
        }
    }

    data class Widerruf(val entfallen: Int, val weiterhin: Int, val rechteGeaendert: Int)

    fun auswirkung(vorher: List<Zugriff>, nachher: List<Zugriff>, regel: Freigabe): Widerruf {
        val verbleibend = nachher.associateBy { it.eintrag.dokument.id }
        val betroffen = vorher.filter {
            val neu = verbleibend[it.eintrag.dokument.id]
            regel in it.urspruenge || neu?.rechte != it.rechte
        }
        val weiterhin = betroffen.mapNotNull { alt -> verbleibend[alt.eintrag.dokument.id]?.let { alt to it } }
        return Widerruf(betroffen.size - weiterhin.size, weiterhin.size,
            weiterhin.count { (alt, neu) -> alt.rechte != neu.rechte })
    }
}
