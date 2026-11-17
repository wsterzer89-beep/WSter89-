
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

                                val faktor = 2

                                val bitmap = Bitmap.createBitmap(
                                    seite.width * faktor,
                                    seite.height * faktor,
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

                                    erkannteTexte.append(
                                        ergebnis.text
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

        return erkannteTexte.toString()
    }
}

