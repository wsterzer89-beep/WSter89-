package de.dokumentenpilot.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchivMigrationTest {
    @Test
    fun migriertAltdatenUndLiestSieNachErneutemOeffnen() {
        val kontext = InstrumentationRegistry.getInstrumentation().targetContext
        val alt = File.createTempFile("namen_", ".txt", kontext.cacheDir)
        val neu = File(alt.parentFile, "${alt.name}.dp")
        val daten = "alte-id.dp|Gehaltsabrechnung März.pdf"
        try {
            alt.writeText(daten)
            assertEquals(daten, GeschuetzteDatei(neu).lesen(alt))
            assertFalse(alt.exists())
            assertFalse(neu.readBytes().toString(Charsets.UTF_8).contains(daten))
            assertEquals(daten, GeschuetzteDatei(neu).lesen(alt))
        } finally {
            alt.delete()
            neu.delete()
        }
    }

    @Test
    fun behaeltAltdatenWennVerschluesselteDateiNichtLesbarIst() {
        val kontext = InstrumentationRegistry.getInstrumentation().targetContext
        val alt = File.createTempFile("namen_", ".txt", kontext.cacheDir)
        val neu = File(alt.parentFile, "${alt.name}.dp")
        try {
            alt.writeText("vorhandene Metadaten")
            neu.writeText("unlesbar")
            assertTrue(runCatching { GeschuetzteDatei(neu).lesen(alt) }.isFailure)
            assertEquals("vorhandene Metadaten", alt.readText())
            assertEquals("unlesbar", neu.readText())
        } finally {
            alt.delete()
            neu.delete()
        }
    }
}
