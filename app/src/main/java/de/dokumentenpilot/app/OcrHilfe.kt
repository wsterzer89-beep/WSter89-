
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

                                    val bloecke = ergebnis.textBlocks
                                    val mitte = bitmap.width / 2
                                    val links = bloecke.filter { it.boundingBox?.let { box -> box.right <= mitte } == true }
                                    val rechts = bloecke.filter { it.boundingBox?.let { box -> box.left >= mitte } == true }
                                    val quer = bloecke.filter { it !in links && it !in rechts }
                                    val zweispaltig = links.size >= 2 && rechts.size >= 2 &&
                                        links.any { l -> rechts.any { r ->
                                            val a = l.boundingBox
                                            val b = r.boundingBox
                                            a != null && b != null && a.top < b.bottom && b.top < a.bottom
                                        } }
                                    val sortiert = if (zweispaltig && quer.all {
                                        val box = it.boundingBox
                                        box != null && (links + rechts).all { spalte ->
                                            val s = spalte.boundingBox
                                            s != null && box.bottom <= s.top
                                        }
                                    }) {
                                        quer.sortedBy { it.boundingBox?.top ?: 0 } +
                                            links.sortedBy { it.boundingBox?.top ?: 0 } +
                                            rechts.sortedBy { it.boundingBox?.top ?: 0 }
                                    } else emptyList()
                                    erkannteTexte.append(
                                        if (sortiert.isEmpty()) ergebnis.text
                                        else sortiert.joinToString("\n\n") { it.text }
                                    )

                                    erkannteTexte.append("\n")

                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    } finally {
                        erkenner.close()
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

