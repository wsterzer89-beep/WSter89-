package de.dokumentenpilot.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

internal object PdfDateien {
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_OCR_SEITEN = 60

    fun lesen(eingabe: InputStream, maximum: Int = MAX_BYTES): ByteArray {
        val puffer = ByteArray(8192)
        val ausgabe = ByteArrayOutputStream()
        while (true) {
            val gelesen = eingabe.read(puffer)
            if (gelesen < 0) break
            require(gelesen <= maximum - ausgabe.size()) { "Datei zu groß" }
            ausgabe.write(puffer, 0, gelesen)
        }
        return ausgabe.toByteArray()
    }

    fun istPdf(daten: ByteArray): Boolean =
        daten.copyOf(minOf(daten.size, 1024)).toString(Charsets.ISO_8859_1).contains("%PDF-")

    fun rendererOeffnen(context: Context, daten: ByteArray): PdfRenderer {
        val datei = File.createTempFile("render_", ".pdf", context.cacheDir)
        try {
            datei.writeBytes(daten)
            val fd = ParcelFileDescriptor.open(datei, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                check(datei.delete()) { "Temporäre PDF konnte nicht entfernt werden" }
                return PdfRenderer(fd)
            } catch (fehler: Exception) {
                fd.close()
                throw fehler
            }
        } finally {
            if (datei.exists()) datei.delete()
        }
    }

    fun rendern(seite: PdfRenderer.Page): Bitmap {
        require(seite.width > 0 && seite.height > 0)
        val faktor = minOf(2.0, 2048.0 / maxOf(seite.width, seite.height))
        val bitmap = Bitmap.createBitmap(
            (seite.width * faktor).toInt().coerceAtLeast(1),
            (seite.height * faktor).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        try {
            bitmap.eraseColor(Color.WHITE)
            val transformation = android.graphics.Matrix().apply {
                setScale(bitmap.width.toFloat() / seite.width, bitmap.height.toFloat() / seite.height)
            }
            seite.render(bitmap, null, transformation, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } catch (fehler: Exception) {
            bitmap.recycle()
            throw fehler
        }
    }
}
