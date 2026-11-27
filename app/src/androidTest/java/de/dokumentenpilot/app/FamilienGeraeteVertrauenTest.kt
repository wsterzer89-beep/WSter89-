package de.dokumentenpilot.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilienGeraeteVertrauenTest {
    @Test fun bestaetigungUndWiderrufBleibenGespeichert() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val prefs = context.applicationContext.getSharedPreferences("familien_geraete_vertrauen_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        try {
            val a = "a".repeat(64)
            val b = "b".repeat(64)
            val store = FamilienGeraeteVertrauen(context)
            assertEquals(FamilienGeraeteVertrauen.Status.NEU, store.status(a))
            assertTrue(store.bestaetigen(a))
            val neu = FamilienGeraeteVertrauen(context)
            assertEquals(FamilienGeraeteVertrauen.Status.BESTAETIGT, neu.status(a))
            assertEquals(FamilienGeraeteVertrauen.Status.ABGELEHNT, neu.status(b))
            assertFalse(neu.bestaetigen(b))
            assertFalse(neu.bestaetigen("ungueltig"))
            assertTrue(neu.widerrufen())
            val danach = FamilienGeraeteVertrauen(context)
            assertFalse(danach.bestaetigen(a))
            assertFalse(danach.bestaetigen(b))
        } finally {
            assertTrue(prefs.edit().clear().commit())
        }
    }
}
