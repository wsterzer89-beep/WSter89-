package de.dokumentenpilot.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** Synthetic fixtures only; never reads archive files. */
internal object FamilienTestPdf {
    const val MAX_BYTES = 65536

    fun erstellen(id: String): ByteArray? {
        val titel = when (id) {
            "demo-1" -> "Synthetische Familienunterlagen"
            "demo-2" -> "Synthetische Vertragsunterlagen"
            else -> return null
        }
        return PdfDocument().use { pdf ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint().apply { textSize = 18f; isAntiAlias = true }
            page.canvas.drawText("DokumentenPilot - TEST", 40f, 60f, paint)
            page.canvas.drawText(titel, 40f, 100f, paint)
            paint.textSize = 12f
            page.canvas.drawText("Kuenstliche Testdaten. Keine privaten Inhalte.", 40f, 140f, paint)
            pdf.finishPage(page)
            ByteArrayOutputStream().use { out ->
                pdf.writeTo(out)
                out.toByteArray().also { require(it.size in 1..MAX_BYTES) }
            }
        }
    }
}
