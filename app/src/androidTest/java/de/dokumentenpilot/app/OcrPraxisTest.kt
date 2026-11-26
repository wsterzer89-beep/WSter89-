package de.dokumentenpilot.app

import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrPraxisTest {
    @Test fun synthetischeDokumente() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val beispiele = listOf(
            "de" to "Geburtsurkunde",
            "en" to "Birth certificate",
            "fr" to "Acte de naissance",
            "es" to "Certificado de nacimiento",
            "pl" to "Akt urodzenia",
            "tr" to "Doğum belgesi",
            "ru" to "Свидетельство о рождении",
            "uk" to "Свідоцтво про народження",
            "ar" to "شهادة الميلاد"
        )
        for ((sprache, titel) in beispiele) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(sprache))
            val lokal = context.createConfigurationContext(config)
            val pdf = PdfDocument()
            val bytes = try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(1000, 500, 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 42f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(titel, 50f, 100f, paint)
                page.canvas.drawText(titel, 50f, 200f, paint)
                page.canvas.drawText(titel, 50f, 300f, paint)
                pdf.finishPage(page)
                ByteArrayOutputStream().use { out -> pdf.writeTo(out); out.toByteArray() }
            } finally { pdf.close() }
            val erkannt = OcrHilfe.textAusPdf(lokal, bytes)
            assertTrue("OCR document family: $sprache", "Geburtsurkunde" in UnterlagenErkennung.angeforderteUnterlagen(erkannt))
        }
    }

    @Test fun zweispaltigeFinanzierungsliste() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.GERMAN)
        val lokal = context.createConfigurationContext(config)
        val pdf = PdfDocument()
        val bytes = try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(1200, 800, 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 30f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Finanzierungsunterlagen", 50f, 70f, paint)
            page.canvas.drawText("1. Geburtsurkunde", 50f, 180f, paint)
            page.canvas.drawText("2. Gehaltsabrechnungen", 50f, 320f, paint)
            page.canvas.drawText("   der letzten 3 Monate", 50f, 360f, paint)
            page.canvas.drawText("3. Energieausweis", 650f, 180f, paint)
            page.canvas.drawText("4. ggf. Stromrechnung", 650f, 320f, paint)
            page.canvas.drawText("5. Fantasieformular", 650f, 460f, paint)
            pdf.finishPage(page)
            ByteArrayOutputStream().use { out -> pdf.writeTo(out); out.toByteArray() }
        } finally { pdf.close() }
        val erkannt = OcrHilfe.textAusPdf(lokal, bytes)
        val punkte = UnterlagenErkennung.listenpunkteErkennen(erkannt)
        assertTrue("Five separate requirements", punkte.size == 5)
        assertTrue("Column reading order", punkte[0].original.contains("Geburtsurkunde") &&
            punkte[1].original.contains("Gehaltsabrechnungen") &&
            punkte[2].original.contains("Energieausweis"))
        assertTrue("Multiline quantity", punkte[1].anforderungen.any { it.anzahl == 3 })
        assertTrue("Conditional requirement", punkte[3].bedingt)
        assertTrue("Unknown requirement retained", punkte[4].anforderungen.isEmpty())
    }

    @Test fun kontrastarmeMehrseitigeDokumente() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.GERMAN)
        val lokal = context.createConfigurationContext(config)
        val pdf = PdfDocument()
        val bytes = try {
            for ((index, titel) in listOf("Geburtsurkunde", "Energieausweis", "Stromrechnung").withIndex()) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(1000, 500, index + 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(135, 135, 135)
                    textSize = 38f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
                page.canvas.drawColor(Color.rgb(235, 235, 235))
                page.canvas.drawText(titel, 50f, 150f, paint)
                pdf.finishPage(page)
            }
            ByteArrayOutputStream().use { out -> pdf.writeTo(out); out.toByteArray() }
        } finally { pdf.close() }
        val erkannt = OcrHilfe.textAusPdf(lokal, bytes)
        val arten = UnterlagenErkennung.angeforderteUnterlagen(erkannt)
        for (titel in listOf("Geburtsurkunde", "Energieausweis", "Stromrechnung")) {
            assertTrue("Low contrast multipage OCR: $titel", titel in arten)
        }
    }

    @Test fun unscharferScan() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.GERMAN)
        val lokal = context.createConfigurationContext(config)
        val bitmap = android.graphics.Bitmap.createBitmap(1000, 500, android.graphics.Bitmap.Config.ARGB_8888)
        val pdf = PdfDocument()
        val bytes = try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 44f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                maskFilter = android.graphics.BlurMaskFilter(2.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawText("Rentenbescheid", 50f, 120f, paint)
            canvas.drawText("Nebenkostenabrechnung", 50f, 250f, paint)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(1000, 500, 1).create())
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdf.finishPage(page)
            ByteArrayOutputStream().use { out -> pdf.writeTo(out); out.toByteArray() }
        } finally {
            pdf.close()
            bitmap.recycle()
        }
        val arten = UnterlagenErkennung.angeforderteUnterlagen(OcrHilfe.textAusPdf(lokal, bytes))
        assertTrue("Blurred pension notice", "Rentenbescheid" in arten)
        assertTrue("Blurred utility statement", "Nebenkostenabrechnung" in arten)
    }
}
