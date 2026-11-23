package de.dokumentenpilot.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDateienTest {
    @Test
    fun liestDateiAnDerGroessengrenzeVollstaendig() {
        val daten = ByteArray(8193) { it.toByte() }
        assertArrayEquals(daten, PdfDateien.lesen(daten.inputStream(), daten.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun verwirftDateiJenseitsDerGroessengrenze() {
        PdfDateien.lesen(ByteArray(8193).inputStream(), 8192)
    }

    @Test
    fun prueftPdfKopfOhneDateinamen() {
        assertTrue(PdfDateien.istPdf("%PDF-1.7\n".toByteArray()))
        assertFalse(PdfDateien.istPdf("kein PDF".toByteArray()))
        assertFalse(PdfDateien.istPdf((" ".repeat(1024) + "%PDF-1.7").toByteArray()))
    }
}
