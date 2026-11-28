package de.dokumentenpilot.app

import de.dokumentenpilot.app.FamilienBerechtigungen.Aktion
import de.dokumentenpilot.app.FamilienBerechtigungen.Bereich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilienFreigabeUebersichtTest {
    private val owner = "owner"
    private val alpha = "alpha"
    private val beta = "beta"
    private val gamma = "gamma"

    private fun eintrag(id: String, kategorie: String = "Verträge") =
        FamilienArchivDaten.Eintrag(FamilienBerechtigungen.Dokument(id, owner, kategorie, setOf("mappe")),
            "Synthetisch $id", mapOf("mappe" to "Belege"))

    private fun regel(peer: String, bereich: Bereich, id: String, vararg rechte: Aktion, automatisch: Boolean = false) =
        FamilienBerechtigungen.Freigabe(peer, bereich, id, rechte.toSet(),
            setOf("a", "b").takeUnless { bereich == Bereich.DOKUMENT }.orEmpty(),
            automatisch, archivEigentuemerId = owner)

    @Test fun ueberlappendeRegelnZaehlenProEmpfaengerEinmalUndPrivateAusnahmenGewinnen() {
        val a = eintrag("a")
        val b = eintrag("b")
        val neu = eintrag("neu")
        val mappe = regel(alpha, Bereich.MAPPE, "mappe", Aktion.ANSEHEN, automatisch = true)
        val direkt = regel(alpha, Bereich.DOKUMENT, "a", Aktion.ANSEHEN, Aktion.SUCHEN)
        val archiv = regel(beta, Bereich.GESAMTES_ARCHIV, "", Aktion.ANSEHEN)
        val dritte = regel(gamma, Bereich.DOKUMENT, "b", Aktion.ANSEHEN, Aktion.ASSISTENT)
        val regeln = FamilienArchivFreigaben.Regeln(listOf(mappe, direkt, archiv, dritte), emptySet())
        val katalog = listOf(a, a, b, neu)
        val fuerAlpha = FamilienFreigabeUebersicht.zugriffe(alpha, katalog, regeln.copy(privat = setOf("b")), emptyList())
        val fuerBeta = FamilienFreigabeUebersicht.zugriffe(beta, katalog, regeln, emptyList())
        val fuerGamma = FamilienFreigabeUebersicht.zugriffe(gamma, katalog, regeln, emptyList())
        assertEquals(listOf("a", "neu"), fuerAlpha.map { it.eintrag.dokument.id })
        assertEquals(listOf(mappe, direkt), fuerAlpha.first().urspruenge)
        assertEquals(listOf("a", "b"), fuerBeta.map { it.eintrag.dokument.id })
        assertEquals(setOf(Aktion.ANSEHEN), fuerBeta.first().rechte)
        assertEquals(listOf("b"), fuerGamma.map { it.eintrag.dokument.id })
        assertEquals(setOf(Aktion.ANSEHEN, Aktion.ASSISTENT), fuerGamma.single().rechte)
    }

    @Test fun widerrufZeigtVerbleibendenZugriffUndGeaenderteRechte() {
        val a = eintrag("a")
        val b = eintrag("b")
        val mappe = regel(alpha, Bereich.MAPPE, "mappe", Aktion.ANSEHEN, Aktion.SUCHEN)
        val direkt = regel(alpha, Bereich.DOKUMENT, "a", Aktion.ANSEHEN)
        val regeln = FamilienArchivFreigaben.Regeln(listOf(mappe, direkt), emptySet())
        val vorher = FamilienFreigabeUebersicht.zugriffe(alpha, listOf(a, b), regeln, emptyList())
        val nachher = FamilienFreigabeUebersicht.zugriffe(alpha, listOf(a, b), regeln.copy(freigaben = listOf(direkt)), emptyList())
        assertEquals(FamilienFreigabeUebersicht.Widerruf(1, 1, 1),
            FamilienFreigabeUebersicht.auswirkung(vorher, nachher, mappe))
        assertTrue(FamilienFreigabeUebersicht.umfasst(nachher.single(), Bereich.KATEGORIE, "Verträge"))
    }
}
