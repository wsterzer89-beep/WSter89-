package de.dokumentenpilot.app

import android.os.SystemClock
import java.net.SocketTimeoutException
import java.util.Base64

/** Worker-thread controller for synthetic pilot only. Close on UI cancellation or lock. */
internal class FamilienTestPilot : AutoCloseable {
    private val scope = FamilienTestLebenszyklus()
    private val sitzung = FamilienTestSitzung { SystemClock.elapsedRealtime() }
    init { scope.registrieren(AutoCloseable { sitzung.beenden() }) }

    fun einladen(adresse: String, anzeigen: (String) -> Unit, bestaetigen: () -> Boolean): Boolean {
        try {
            val identitaet = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer(identitaet, adresse))
            val token = sitzung.einladungsToken() ?: return false
            val code = try {
                FamilienTestEinladungFormat.kodieren(FamilienTestEinladungFormat.Einladung(
                    adresse, server.port, Base64.getUrlEncoder().withoutPadding().encodeToString(token),
                    identitaet.fingerprint))
            } finally { token.fill(0) }
            anzeigen(code)
            while (scope.aktiv()) {
                val peer = try { server.annehmen(scope) } catch (_: SocketTimeoutException) { continue }
                server.close()
                return FamilienTestProtokoll.senden(peer, sitzung) { scope.aktiv() && bestaetigen() && scope.aktiv() }
            }
            return false
        } finally { close() }
    }

    fun beitreten(code: String, bestaetigen: () -> Boolean): String? {
        try {
            val einladung = FamilienTestEinladungFormat.lesen(code) ?: return null
            if (!scope.aktiv()) return null
            val peer = FamilienTestVerbindung.verbinden(einladung, scope)
            return FamilienTestProtokoll.empfangen(peer, einladung) { scope.aktiv() && bestaetigen() && scope.aktiv() }
                ?.takeIf { scope.aktiv() }
        } finally { close() }
    }

    fun wlanUeberwachen(cm: android.net.ConnectivityManager) {
        scope.registrieren(FamilienTestWlan.beobachten(cm, this))
    }

    fun aktiv(): Boolean = scope.aktiv()

    override fun close() { sitzung.beenden(); scope.close() }
}
