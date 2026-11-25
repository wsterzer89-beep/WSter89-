package de.dokumentenpilot.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

internal class LokaleSchriftOcr private constructor(
    private val api: TessBaseAPI
) : AutoCloseable {
    fun text(bitmap: Bitmap): String {
        try {
            api.setImage(bitmap)
            return api.getUTF8Text().orEmpty()
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
                return LokaleSchriftOcr(api)
            } catch (fehler: Throwable) {
                api.recycle()
                throw fehler
            }
        }
    }
}
