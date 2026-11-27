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

    /** Explicit enrollment only after verified identity and local consent. */
    fun weiteresGeraetBestaetigen(fingerprint: String): Boolean = synchronized(lock) {
        runCatching {
            if (!gueltig(fingerprint) || prefs.getBoolean("widerrufen", false)) return@synchronized false
            val alt = prefs.getString("peer", null)
            if (alt != null && !gueltig(alt)) return@synchronized false
            val gesperrt = prefs.getStringSet("gesperrte_peers", emptySet()).orEmpty()
            if (fingerprint in gesperrt) return@synchronized false
            val weitere = prefs.getStringSet("weitere_peers", emptySet()).orEmpty()
            if (weitere.any { !gueltig(it) }) return@synchronized false
            if (fingerprint == alt || fingerprint in weitere) return@synchronized true
            if (weitere.size >= 15) return@synchronized false
            prefs.edit().putStringSet("weitere_peers", weitere + fingerprint).commit()
        }.getOrDefault(false)
    }

    fun geraetWiderrufen(fingerprint: String): Boolean = synchronized(lock) {
        runCatching {
            if (fingerprint !in bestaetigtePeers()) return@synchronized false
            val gesperrt = prefs.getStringSet("gesperrte_peers", emptySet()).orEmpty()
            prefs.edit().putStringSet("gesperrte_peers", gesperrt + fingerprint).commit()
        }.getOrDefault(false)
    }

    /** Only for an explicitly initiated additional-device pairing session. */
    fun aufnahmeStatus(fingerprint: String): Status = synchronized(lock) {
        val aktuell = statusIntern(fingerprint)
        if (aktuell != Status.ABGELEHNT) return@synchronized aktuell
        runCatching {
            if (!gueltig(fingerprint) || prefs.getBoolean("widerrufen", false)) return@synchronized Status.ABGELEHNT
            val alt = prefs.getString("peer", null)
            val weitere = prefs.getStringSet("weitere_peers", emptySet()).orEmpty()
            val gesperrt = prefs.getStringSet("gesperrte_peers", emptySet()).orEmpty()
            if ((alt != null && !gueltig(alt)) || weitere.any { !gueltig(it) } ||
                fingerprint in gesperrt || weitere.size >= 15) Status.ABGELEHNT else Status.NEU
        }.getOrDefault(Status.ABGELEHNT)
    }

    fun geraeteName(fingerprint: String): String? = synchronized(lock) {
        if (statusIntern(fingerprint) != Status.BESTAETIGT) return@synchronized null
        runCatching { prefs.getString("name:$fingerprint", null) }.getOrNull()
    }

    fun geraeteNameSpeichern(fingerprint: String, name: String): Boolean = synchronized(lock) {
        if (statusIntern(fingerprint) != Status.BESTAETIGT) return@synchronized false
        val sauber = name.trim()
        if (sauber.length > 60 || sauber.any { it.isISOControl() }) return@synchronized false
        runCatching {
            val editor = prefs.edit()
            if (sauber.isEmpty()) editor.remove("name:$fingerprint") else editor.putString("name:$fingerprint", sauber)
            editor.commit()
        }.getOrDefault(false)
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
            if (fingerprint in prefs.getStringSet("gesperrte_peers", emptySet()).orEmpty()) return Status.ABGELEHNT
            if (gespeichert != null && !gueltig(gespeichert)) return Status.ABGELEHNT
            if (fingerprint in prefs.getStringSet("weitere_peers", emptySet()).orEmpty()) return Status.BESTAETIGT
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
