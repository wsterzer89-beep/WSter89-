package de.dokumentenpilot.app

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

object OcrHilfe {
    fun textAusPdf(context: Context, pdfDaten: ByteArray): String {
        val texte = StringBuilder()
        PdfDateien.rendererOeffnen(context, pdfDaten).use { renderer ->
            require(renderer.pageCount in 1..PdfDateien.MAX_OCR_SEITEN) { "Zu viele Seiten für OCR" }
            val erkenner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                for (index in 0 until renderer.pageCount) {
                    val bitmap = renderer.openPage(index).use(PdfDateien::rendern)
                    try {
                        val ergebnis = Tasks.await(
                            erkenner.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS
                        )
                        texte.appendLine(ergebnis.text)
                    } finally {
                        bitmap.recycle()
                    }
                }
            } finally {
                erkenner.close()
            }
        }
        return texte.toString().trim().also {
            check(it.isNotBlank()) { "Kein Text im PDF erkannt" }
        }
    }
}
