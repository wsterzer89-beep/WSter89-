package de.dokumentenpilot.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MappenSpeicherTest {
    @Test
    fun suchkriterienBleibenErhalten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datei = File(context.filesDir, "unterlagen_mappen.json")
        val backup = if (datei.exists()) datei.readBytes() else null
        try {
            val speicher = UnterlagenMappen(context)
            val mappe = speicher.neueMappe("Test")
            mappe.anforderungen.add(UnterlagenAnforderung("Kontoauszüge", 3, false, 2026, 1, 3, "Testperson"))
            mappe.anforderungen.add(UnterlagenAnforderung("Energieausweis", 1, false))
            mappe.dokumentIds.add("test-id")
            mappe.manuelleNachweise["Kontoauszüge"] = mutableListOf("test-id")
            speicher.speichern(listOf(mappe))
            assertEquals(mappe, UnterlagenMappen(context).laden().single())
            datei.writeText("""[{"id":"alt","name":"Alt","dokumentIds":[],"anforderungen":[{"dokumentenart":"Energieausweis","anzahl":1}]}]""")
            val alt = speicher.laden().single().anforderungen.single()
            assertNull(alt.monatVon)
            assertNull(alt.monatBis)
            assertNull(alt.namensFilter)
        } finally {
            if (backup == null) datei.delete() else datei.writeBytes(backup)
        }
    }
}
