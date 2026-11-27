package de.dokumentenpilot.app

import android.content.Context

/** Isolated synthetic pilot trust store. Never grants archive permissions.
 * One peer per installation; a revoked peer cannot silently enroll again.
 */
internal class FamilienGeraeteVertrauen private constructor(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(
        "familien_geraete_vertrauen_v1", Context.MODE_PRIVATE))

    enum class Status { NEU, BESTAETIGT, ABGELEHNT }

    enum class Kopplung { NICHT_GEKOPPELT, LOKAL_BESTAETIGT, GESPERRT, UNBEKANNT }

    fun kopplung(): Kopplung = synchronized(lock) {
        try {
            val peer = prefs.getString("peer", null)
            when {
                prefs.getBoolean("widerrufen", false) -> Kopplung.GESPERRT
                peer == null -> Kopplung.NICHT_GEKOPPELT
                gueltig(peer) -> Kopplung.LOKAL_BESTAETIGT
                else -> Kopplung.UNBEKANNT
            }
        } catch (_: Exception) { Kopplung.UNBEKANNT }
    }

    fun bestaetigterPeer(): String? = synchronized(lock) {
        runCatching {
            prefs.getString("peer", null)?.takeIf { statusIntern(it) == Status.BESTAETIGT }
        }.getOrNull()
    }

    /** Existing single-peer pilot remains the default until explicit multi-peer enrollment is wired. */
    fun bestaetigtePeers(): Set<String> = synchronized(lock) {
        runCatching {
            if (prefs.getBoolean("widerrufen", false)) return@synchronized emptySet<String>()
            val alt = prefs.getString("peer", null)
            val weitere = prefs.getStringSet("weitere_peers", emptySet()).orEmpty()
            val gesperrt = prefs.getStringSet("gesperrte_peers", emptySet()).orEmpty()
            (weitere + listOfNotNull(alt)).filter { gueltig(it) && it !in gesperrt }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun gueltig(fingerprint: String) = fingerprint.matches(Regex("[0-9a-f]{64}"))

    fun status(fingerprint: String): Status = synchronized(lock) {
        statusIntern(fingerprint)
    }

    private fun statusIntern(fingerprint: String): Status {
        if (!gueltig(fingerprint)) return Status.ABGELEHNT
        return try {
            if (prefs.getBoolean("widerrufen", false)) return Status.ABGELEHNT
            val gespeichert = prefs.getString("peer", null)
            when {
                gespeichert == null -> Status.NEU
                gespeichert == fingerprint -> Status.BESTAETIGT
                else -> Status.ABGELEHNT
            }
        } catch (_: Exception) { Status.ABGELEHNT }
    }

    /** Call only after proof of possession and explicit local confirmation. */
    fun bestaetigen(fingerprint: String): Boolean = synchronized(lock) {
        when (statusIntern(fingerprint)) {
            Status.ABGELEHNT -> false
            Status.BESTAETIGT -> true
            Status.NEU -> prefs.edit().putString("peer", fingerprint).commit()
        }
    }

    /** Revocation is sticky; no automatic reset or replacement is provided. */
    fun widerrufen(): Boolean = synchronized(lock) {
        prefs.edit().putBoolean("widerrufen", true).commit()
    }

    companion object {
        private val lock = Any()

        @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
        fun nurTest(prefs: android.content.SharedPreferences) = FamilienGeraeteVertrauen(prefs)
    }
}
