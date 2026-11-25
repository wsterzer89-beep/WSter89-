package de.dokumentenpilot.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

internal class LokaleSchriftOcr private constructor(
    private val api: TessBaseAPI,
    private val rechtsNachLinks: Boolean
) : AutoCloseable {
    var letzteKonfidenz: Int = 0
        private set

    fun text(bitmap: Bitmap): String {
        letzteKonfidenz = 0
        try {
            api.setImage(bitmap)
            val original = api.getUTF8Text().orEmpty()
            letzteKonfidenz = api.meanConfidence().coerceIn(0, 100)
            if (original.isBlank()) return original
            val iterator = api.getResultIterator() ?: return original
            val bloecke = mutableListOf<OcrLesereihenfolge.Block>()
            val ebene = TessBaseAPI.PageIteratorLevel.RIL_BLOCK
            try {
                iterator.begin()
                do {
                    val text = iterator.getUTF8Text(ebene).orEmpty()
                    val rect = iterator.getBoundingRect(ebene) ?: return original
                    if (text.isNotBlank()) {
                        bloecke.add(OcrLesereihenfolge.Block(text, rect.left, rect.top, rect.right, rect.bottom))
                    }
                } while (iterator.next(ebene))
            } finally {
                iterator.delete()
            }
            return OcrLesereihenfolge.sortieren(bloecke, bitmap.width, rechtsNachLinks)
                ?.joinToString("\n\n") { it.text } ?: original
        } finally {
            api.clear()
        }
    }

    override fun close() = api.recycle()

    companion object {
        fun oeffnen(context: Context, sprache: String): LokaleSchriftOcr {
            require(sprache in setOf("ara", "rus", "ukr"))
            val basis = File(context.noBackupFilesDir, "ocr-modelle-v1")
            val daten = File(basis, "tessdata")
            check(daten.isDirectory || daten.mkdirs())
            val ziel = File(daten, "$sprache.traineddata")
            val temp = File.createTempFile("modell-", ".tmp", daten)
            try {
                context.assets.open("tessdata/$sprache.traineddata").use { eingabe ->
                    temp.outputStream().use { ausgabe -> eingabe.copyTo(ausgabe) }
                }
                check(temp.length() > 0)
                check(temp.renameTo(ziel))
            } finally {
                temp.delete()
            }
            val api = TessBaseAPI()
            try {
                check(api.init(basis.absolutePath, sprache))
                return LokaleSchriftOcr(api, sprache == "ara")
            } catch (fehler: Throwable) {
                api.recycle()
                throw fehler
            }
        }
    }
}
