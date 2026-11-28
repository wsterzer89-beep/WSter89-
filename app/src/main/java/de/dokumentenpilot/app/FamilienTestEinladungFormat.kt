package de.dokumentenpilot.app

import java.util.Base64

/** QR payload only; does not establish trust or open a network connection. */
internal object FamilienTestEinladungFormat {
    data class Einladung(val host: String, val port: Int, val token: String, val fingerprint: String)
    private val hex = Regex("[0-9a-f]{64}")
    private val tokenFormat = Regex("[A-Za-z0-9_-]{43}")

    fun kodieren(e: Einladung): String {
        require(gueltig(e))
        return "DPF1|${e.host}|${e.port}|${e.token}|${e.fingerprint}"
    }

    fun lesen(text: String): Einladung? {
        if (text.length > 160) return null
        val teile = text.split('|')
        if (teile.size != 5 || teile[0] != "DPF1") return null
        val e = Einladung(teile[1], teile[2].toIntOrNull() ?: return null, teile[3], teile[4])
        return e.takeIf { gueltig(it) }
    }

    private fun gueltig(e: Einladung): Boolean {
        val ip = e.host.split('.')
        if (ip.size != 4 || ip.any { it.length !in 1..3 || it.any { c -> c !in '0'..'9' } ||
                (it.toIntOrNull() ?: -1) !in 0..255 || (it.length > 1 && it.startsWith("0")) }) return false
        val a = ip[0].toInt()
        val b = ip[1].toInt()
        if (!(a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168))) return false
        if (e.port !in 1024..65535 || !hex.matches(e.fingerprint) || !tokenFormat.matches(e.token)) return false
        return try {
            val bytes = Base64.getUrlDecoder().decode(e.token)
            bytes.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == e.token
        } catch (_: IllegalArgumentException) { false }
    }
}
