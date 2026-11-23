package de.dokumentenpilot.app

import android.util.AtomicFile
import java.io.File

internal class GeschuetzteDatei(private val pfad: File) {
    private val datei = AtomicFile(pfad)

    fun lesen(alt: File? = null, altLesen: () -> String? = { alt?.takeIf { it.exists() }?.readText() }): String? {
        val text = if (datei.baseFile.exists() || File(pfad.parentFile, "${pfad.name}.bak").exists()) {
            DokumentKrypto.datenEntschluesseln(datei.readFully()).toString(Charsets.UTF_8)
        } else {
            val bisher = altLesen() ?: return null
            schreiben(bisher)
            bisher
        }
        alt?.delete()
        return text
    }

    fun schreiben(text: String) {
        val verschluesselt = DokumentKrypto.datenVerschluesseln(text.toByteArray(Charsets.UTF_8))
        val ausgabe = datei.startWrite()
        try {
            ausgabe.write(verschluesselt)
        } catch (fehler: Exception) {
            datei.failWrite(ausgabe)
            throw fehler
        }
        datei.finishWrite(ausgabe)
        check(datei.readFully().contentEquals(verschluesselt))
    }
}
