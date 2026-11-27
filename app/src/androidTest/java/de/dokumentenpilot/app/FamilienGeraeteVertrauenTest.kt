package de.dokumentenpilot.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilienGeraeteVertrauenTest {
    @Test fun dokumentrechteSindAnBestaetigtesGeraetGebunden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("familien_rechte_instrumentation", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        try {
            val store = FamilienGeraeteVertrauen.nurTest(prefs)
            val peer = "a".repeat(64)
            val fremd = "b".repeat(64)
            val doc = FamilienBerechtigungen.Dokument("demo-1", "demo-owner", "Demo")
            val ansehen = FamilienBerechtigungen.Aktion.ANSEHEN
            val regel = FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.DOKUMENT,
                doc.id, setOf(ansehen), archivEigentuemerId = "demo-owner")
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen, listOf(regel)))
            assertTrue(store.bestaetigen(peer))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen, emptyList()))
            assertTrue(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen, listOf(regel)))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, fremd, doc, ansehen, listOf(regel)))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc.copy(id = "demo-2"), ansehen, listOf(regel)))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen, listOf(regel.copy(widerrufen = true))))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, FamilienBerechtigungen.Aktion.EXPORTIEREN, listOf(regel)))
            val mappe = doc.copy(mappenIds = setOf("familie"))
            val neu = mappe.copy(id = "demo-2")
            for ((bereich, id) in listOf(
                FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV to "",
                FamilienBerechtigungen.Bereich.KATEGORIE to "Demo",
                FamilienBerechtigungen.Bereich.MAPPE to "familie"
            )) {
                val umfang = regel.copy(bereich = bereich, bereichId = id,
                    dokumentIdsBeiFreigabe = setOf(doc.id))
                assertTrue(FamilienGeraeteRechte.erlaubt(store, peer, mappe, ansehen, listOf(umfang)))
                assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, neu, ansehen, listOf(umfang)))
                assertTrue(FamilienGeraeteRechte.erlaubt(store, peer, neu, ansehen,
                    listOf(umfang.copy(neueDokumenteAutomatisch = true))))
                assertFalse(FamilienGeraeteRechte.erlaubt(store, peer,
                    mappe.copy(eigentuemerId = "privat"), ansehen, listOf(umfang)))
            }
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, mappe.copy(kategorie = "Privat"), ansehen,
                listOf(regel.copy(bereich = FamilienBerechtigungen.Bereich.KATEGORIE, bereichId = "Demo",
                    neueDokumenteAutomatisch = true))))
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen,
                listOf(regel.copy(bereich = FamilienBerechtigungen.Bereich.MAPPE, bereichId = "familie",
                    neueDokumenteAutomatisch = true))))
            assertTrue(store.widerrufen())
            assertFalse(FamilienGeraeteRechte.erlaubt(store, peer, doc, ansehen, listOf(regel)))
        } finally {
            assertTrue(prefs.edit().clear().commit())
        }
    }

    @Test fun bestaetigungUndWiderrufBleibenGespeichert() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("familien_geraete_vertrauen_instrumentation", Context.MODE_PRIVATE)
        assertTrue("Isolierte Testeinstellungen konnten nicht geleert werden", prefs.edit().clear().commit())
        try {
            val a = "a".repeat(64)
            val b = "b".repeat(64)
            val store = FamilienGeraeteVertrauen.nurTest(prefs)
            assertEquals(FamilienGeraeteVertrauen.Status.NEU, store.status(a))
            assertEquals(FamilienGeraeteVertrauen.Kopplung.NICHT_GEKOPPELT, store.kopplung())
            assertTrue(store.bestaetigen(a))
            val neu = FamilienGeraeteVertrauen.nurTest(prefs)
            assertEquals(FamilienGeraeteVertrauen.Kopplung.LOKAL_BESTAETIGT, neu.kopplung())
            assertEquals(FamilienGeraeteVertrauen.Status.BESTAETIGT, neu.status(a))
            assertEquals(FamilienGeraeteVertrauen.Status.ABGELEHNT, neu.status(b))
            assertFalse(neu.bestaetigen(b))
            assertFalse(neu.bestaetigen("ungueltig"))
            assertTrue(neu.widerrufen())
            val danach = FamilienGeraeteVertrauen.nurTest(prefs)
            assertEquals(FamilienGeraeteVertrauen.Kopplung.GESPERRT, danach.kopplung())
            assertFalse(danach.bestaetigen(a))
            assertFalse(danach.bestaetigen(b))
            assertTrue(prefs.edit().clear().putString("peer", "ungueltig").commit())
            assertEquals(FamilienGeraeteVertrauen.Kopplung.UNBEKANNT, danach.kopplung())
            assertEquals(FamilienGeraeteVertrauen.Status.ABGELEHNT, danach.status(a))
        } finally {
            assertTrue("Isolierte Testeinstellungen konnten nicht geleert werden", prefs.edit().clear().commit())
        }
    }
}
