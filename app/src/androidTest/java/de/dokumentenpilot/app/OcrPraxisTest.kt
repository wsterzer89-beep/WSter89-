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
}
