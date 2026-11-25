package de.dokumentenpilot.app

internal object ArchivAnzeige {
    fun passt(kategoriePasst: Boolean, name: String, ocr: String, suche: String): Boolean =
        kategoriePasst && (suche.isBlank() || AssistentSuche.ocrSuchePasst(name, ocr, suche))
}
