package de.dokumentenpilot.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal class PdfFreigaben(private val context: Context) {
    private val cache = File(context.cacheDir, "pdf_freigaben")
    private val authority = "${context.packageName}.fileprovider"

    fun neuerOrdner(): File = File(cache, UUID.randomUUID().toString()).apply {
        check(mkdirs())
        ablaufPlanen(this, GUELTIGKEIT)
    }

    fun uri(datei: File) = FileProvider.getUriForFile(context, authority, datei)

    fun entfernen(ordner: File) {
        ordner.listFiles()?.forEach { datei ->
            runCatching {
                context.revokeUriPermission(uri(datei), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        ordner.deleteRecursively()
    }

    fun alteDateienEntfernen() {
        for (name in listOf("pdf_vorschau", "pdf_versand", "pdf_einzelversand")) {
            runCatching {
                context.revokeUriPermission(
                    android.net.Uri.parse("content://$authority/$name"),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            File(context.cacheDir, name).deleteRecursively()
        }
        context.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith("render_") || it.name.startsWith("ocr_")) {
                if (System.currentTimeMillis() - it.lastModified() >= GUELTIGKEIT) it.delete()
            }
        }
        cache.listFiles()?.forEach {
            val rest = GUELTIGKEIT - (System.currentTimeMillis() - it.lastModified()).coerceAtLeast(0)
            if (rest <= 0) entfernen(it) else ablaufPlanen(it, rest)
        }
    }

    private fun ablaufPlanen(ordner: File, dauer: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ entfernen(ordner) }, dauer)
    }

    private companion object {
        const val GUELTIGKEIT = 15 * 60 * 1000L
    }
}
