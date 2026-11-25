
package de.dokumentenpilot.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit

object OcrHilfe {

    @Synchronized
    fun alteTempDateienEntfernen(context: Context) {
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("ocr_") && it.name.endsWith(".pdf")
        }?.forEach { it.delete() }
    }

    @Synchronized

    fun textAusPdf(
        context: Context,
        pdfDaten: ByteArray
    ): String {

        val temporaereDatei = File.createTempFile(
            "ocr_",
            ".pdf",
            context.cacheDir
        )

        val erkannteTexte = StringBuilder()

        try {
            temporaereDatei.writeBytes(pdfDaten)

            ParcelFileDescriptor.open(
                temporaereDatei,
                ParcelFileDescriptor.MODE_READ_ONLY
            ).use { dateiDeskriptor ->

                PdfRenderer(dateiDeskriptor).use { renderer ->

                    val erkenner = TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                    )

                    val sprache = when (context.resources.configuration.locales[0].language) {
                        "ar" -> "ara"
                        "ru" -> "rus"
                        "uk" -> "ukr"
                        else -> null
                    }
                    val lokaleOcr = mutableMapOf<String, LokaleSchriftOcr>()
                    try {
                        for (seiteNummer in 0 until renderer.pageCount) {

                            renderer.openPage(seiteNummer).use { seite ->

                                val faktor = minOf(2.0, 2400.0 / maxOf(seite.width, seite.height))

                                val bitmap = Bitmap.createBitmap(
                                    (seite.width * faktor).toInt().coerceAtLeast(1),
                                    (seite.height * faktor).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )

                                try {
                                    bitmap.eraseColor(Color.WHITE)

                                    seite.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )

                                    val bild = InputImage.fromBitmap(
                                        bitmap,
                                        0
                                    )

                                    val ergebnis = Tasks.await(
                                        erkenner.process(bild),
                                        60,
                                        TimeUnit.SECONDS
                                    )

                                    val bloecke = ergebnis.textBlocks.mapNotNull { block ->
                                        block.boundingBox?.let {
                                            OcrLesereihenfolge.Block(block.text, it.left, it.top, it.right, it.bottom)
                                        }
                                    }
                                    val sortiert = if (bloecke.size == ergebnis.textBlocks.size)
                                        OcrLesereihenfolge.sortieren(bloecke, bitmap.width) else null
                                    val latein = sortiert?.joinToString("\n\n") { it.text } ?: ergebnis.text
                                    // Prefer the configured script; try other scripts when Latin OCR is sparse.
                                    val kandidaten = if (sprache != null || latein.count { it.isLetter() } < 20)
                                        (listOfNotNull(sprache) + listOf("ara", "rus", "ukr")).distinct()
                                    else emptyList()
                                    var besterText = latein
                                    var besteAnzahl = latein.count { it.isLetter() }
                                    for (modell in kandidaten) {
                                        val engine = lokaleOcr.getOrPut(modell) { LokaleSchriftOcr.oeffnen(context, modell) }
                                        val text = engine.text(bitmap)
                                        val bewertung = OcrBewertung.bewertung(text, modell, engine.letzteKonfidenz)
                                        if (engine.letzteKonfidenz >= 50 && bewertung > besteAnzahl) {
                                            besterText = text
                                            besteAnzahl = bewertung
                                        }
                                    }
                                    erkannteTexte.append(besterText)

                                    erkannteTexte.append("\n")

                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    } finally {
                        try {
                            lokaleOcr.values.forEach { it.close() }
                        } finally {
                            erkenner.close()
                        }
                    }
                }
            }

        } finally {
            temporaereDatei.delete()
        }

        return erkannteTexte.toString().trim().also {
            check(it.isNotBlank()) { "Kein Text im PDF erkannt" }
        }
    }
}

