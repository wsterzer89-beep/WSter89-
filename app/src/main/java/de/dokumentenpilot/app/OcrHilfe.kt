
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
                                    val strukturierteListe = UnterlagenErkennung.listenpunkteErkennen(latein).size >= 2
                                    val lateinArten = SynonymKatalog.erkannteArten(latein)
                                    val lateinBuchstaben = latein.count { it.isLetter() }
                                    // A Latin result does not rule out other scripts on the same page.
                                    val kandidaten = (listOfNotNull(sprache) + listOf("ara", "rus", "ukr")).distinct()
                                    // Structured Latin lists already suppress supplemental script lines.
                                    // Preserve the existing fallback for non-Latin locales and weak Latin results.
                                    val aktiveKandidaten = if (strukturierteListe && sprache == null && lateinBuchstaben >= 20)
                                        emptyList() else kandidaten
                                    val schriftTexte = mutableMapOf<Character.UnicodeScript, Pair<Int, String>>()
                                    var besterText = latein
                                    var besteAnzahl = lateinBuchstaben
                                    var besteArten = lateinArten.size
                                    for (modell in aktiveKandidaten) {
                                        val (engine, text) = try {
                                            val lokal = lokaleOcr.getOrPut(modell) { LokaleSchriftOcr.oeffnen(context, modell) }
                                            lokal to lokal.text(bitmap)
                                        } catch (fehler: InterruptedException) {
                                            Thread.currentThread().interrupt()
                                            throw fehler
                                        } catch (fehler: Exception) {
                                            // An optional model must not discard other OCR results.
                                            continue
                                        }
                                        val bewertung = OcrBewertung.bewertung(text, modell, engine.letzteKonfidenz)
                                        val schrift = if (modell == "ara") Character.UnicodeScript.ARABIC else Character.UnicodeScript.CYRILLIC
                                        val schriftZeilen = text.lineSequence().filter { zeile ->
                                            val buchstaben = zeile.filter { it.isLetter() }
                                            val passend = buchstaben.count { Character.UnicodeScript.of(it.code) == schrift }
                                            passend >= 4 && passend * 2 >= buchstaben.length
                                        }.joinToString("\n")
                                        if (schriftZeilen.isNotBlank() &&
                                            (engine.letzteKonfidenz + 1) > (schriftTexte[schrift]?.first ?: 0)) {
                                            schriftTexte[schrift] = (engine.letzteKonfidenz + 1) to schriftZeilen
                                        }
                                        val erkannteArten = SynonymKatalog.erkannteArten(text).size
                                        if ((sprache != null || lateinBuchstaben < 20) && engine.letzteKonfidenz >= 50 && bewertung > 0 &&
                                            (erkannteArten > besteArten || (erkannteArten == besteArten && bewertung > besteAnzahl))) {
                                            besterText = text
                                            besteAnzahl = bewertung
                                            besteArten = erkannteArten
                                        }
                                    }
                                    erkannteTexte.append(besterText)
                                    // Keep the Latin result if the selected model lost known document families.
                                    if (!SynonymKatalog.erkannteArten(besterText).containsAll(lateinArten)) {
                                        val vorhandeneZeilen = besterText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                        val ergaenzung = latein.lineSequence().filter { it.trim() !in vorhandeneZeilen }.joinToString("\n")
                                        if (ergaenzung.isNotBlank()) erkannteTexte.append("\n").append(ergaenzung)
                                    }
                                    // Preserve structured Latin checklists without appending competing OCR lines.
                                    for ((schrift, kandidat) in schriftTexte) {
                                        if (strukturierteListe) break
                                        if (besterText.none { Character.UnicodeScript.of(it.code) == schrift && it.isLetter() }) {
                                            erkannteTexte.append("\n").append(kandidat.second)
                                        }
                                    }

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

