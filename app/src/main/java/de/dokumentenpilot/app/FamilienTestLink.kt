package de.dokumentenpilot.app

import android.content.Intent
import java.util.Base64

internal object FamilienTestLink {
    private const val PREFIX = "dokumentenpilot://familie/einladung/"
    fun erstellen(code: String): String {
        require(FamilienTestEinladungFormat.lesen(code) != null)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(code.toByteArray(Charsets.UTF_8))
    }

    fun lesen(link: String): String? {
        if (link.length > 256 || !link.startsWith(PREFIX)) return null
        val payload = link.removePrefix(PREFIX)
        if (!Regex("[A-Za-z0-9_-]{1,214}").matches(payload)) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(payload)
            if (Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != payload) return null
            val code = bytes.toString(Charsets.UTF_8)
            code.takeIf { FamilienTestEinladungFormat.lesen(it) != null }
        } catch (_: IllegalArgumentException) { null }
    }

    fun teilen(code: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, erstellen(code))
        }, "Familieneinladung teilen")
}
