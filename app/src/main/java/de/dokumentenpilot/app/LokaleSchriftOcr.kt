package de.dokumentenpilot.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.security.MessageDigest

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
            val erwartet = when (sprache) {
                "ara" -> "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"
                "rus" -> "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225"
                else -> "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b"
            }
            fun gueltig(datei: File): Boolean {
                if (!datei.isFile) return false
                val digest = MessageDigest.getInstance("SHA-256")
                datei.inputStream().use { eingabe ->
                    val puffer = ByteArray(8192)
                    while (true) {
                        val anzahl = eingabe.read(puffer)
                        if (anzahl < 0) break
                        digest.update(puffer, 0, anzahl)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == erwartet
            }
            if (!gueltig(ziel)) {
                val temp = File.createTempFile("modell-", ".tmp", daten)
                try {
                    context.assets.open("tessdata/$sprache.traineddata").use { eingabe ->
                        temp.outputStream().use { ausgabe -> eingabe.copyTo(ausgabe) }
                    }
                    check(gueltig(temp))
                    check(temp.renameTo(ziel))
                } finally {
                    temp.delete()
                }
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
