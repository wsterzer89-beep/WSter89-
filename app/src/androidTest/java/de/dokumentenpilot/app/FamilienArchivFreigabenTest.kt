package de.dokumentenpilot.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilienArchivFreigabenTest {
    @Test fun gespeicherteRegelnTrennenGeraeteUndBeruecksichtigenWiderruf() {
        val basis = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "archiv_instrumentation_${java.util.UUID.randomUUID()}_"
        val context = object : ContextWrapper(basis) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(prefix + name, mode)
        }
        val owner = "a".repeat(64)
        val peer = "b".repeat(64)
        val anderesGeraet = "c".repeat(64)
        val doc = FamilienBerechtigungen.Dokument("demo-archiv-1", owner, "Testkategorie", setOf("testmappe"))
        val neu = doc.copy(id = "demo-archiv-2")
        val ansehen = FamilienBerechtigungen.Aktion.ANSEHEN
        val regel = FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.MAPPE,
            "testmappe", setOf(ansehen), setOf(doc.id), archivEigentuemerId = owner)
        val regeln = FamilienArchivFreigaben.Regeln(listOf(regel), setOf(doc.id))
        val trust = FamilienGeraeteVertrauen(context)
        try {
            val store = FamilienArchivFreigaben(context)
            assertFalse(store.speichern(owner, peer, regeln, listOf(doc)))
            assertTrue(trust.bestaetigen(peer))
            assertTrue(trust.weiteresGeraetBestaetigen(anderesGeraet))
            assertTrue(store.speichern(owner, peer, regeln, listOf(doc)))
            val erneut = FamilienArchivFreigaben(context)
            assertEquals(regeln, erneut.lesen(owner, peer))
            assertTrue(erneut.lesen(owner, anderesGeraet)!!.freigaben.isEmpty())

            val zugriff = FamilienArchivZugriff(owner,
                bestaetigt = { trust.status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT },
                aktiv = { true }, katalog = { listOf(doc, neu) },
                freigaben = { erneut.lesen(owner, peer)?.freigaben.orEmpty() },
                privat = { erneut.lesen(owner, peer)?.privat.orEmpty() },
                lesen = { error("Berechtigungsprüfung darf keine PDF entschlüsseln") }
            )
            assertFalse(zugriff.darfAnsehen(peer, doc.id))
            assertFalse(zugriff.darfAnsehen(peer, neu.id))
            val automatisch = regeln.copy(freigaben = listOf(regel.copy(neueDokumenteAutomatisch = true)))
            assertTrue(erneut.speichern(owner, peer, automatisch, listOf(doc, neu)))
            assertTrue(zugriff.darfAnsehen(peer, neu.id))
            assertFalse(zugriff.darfAnsehen(anderesGeraet, neu.id))
            assertTrue(trust.geraetWiderrufen(peer))
            assertFalse(zugriff.darfAnsehen(peer, neu.id))
            assertNull(erneut.lesen(owner, peer))
        } finally {
            basis.deleteSharedPreferences(prefix + "familien_archiv_regeln_v1")
            basis.deleteSharedPreferences(prefix + "familien_geraete_vertrauen_v1")
        }
    }
}
