package de.dokumentenpilot.app

import android.os.SystemClock
import java.net.SocketTimeoutException
import java.util.Base64

/** Worker-thread controller for synthetic pilot only. Close on UI cancellation or lock. */
internal class FamilienTestPilot : AutoCloseable {
    private var bindung: FamilienTestWlan.Bindung? = null
    private fun wlan(): FamilienTestWlan.Bindung = checkNotNull(bindung).also { it.pruefen() }
    private val scope = FamilienTestLebenszyklus()
    private val sitzung = FamilienTestSitzung { SystemClock.elapsedRealtime() }
    init { scope.registrieren(AutoCloseable { sitzung.beenden() }) }

    fun einladen(adresse: String, anzeigen: (String) -> Unit, bestaetigen: () -> Boolean): Boolean {
        try {
            val netz = wlan()
            check(adresse == netz.ip)
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
                netz.pruefen()
                server.close()
                return FamilienTestProtokoll.senden(peer, sitzung) { aktiv() && bestaetigen() && aktiv() }
            }
            return false
        } finally { close() }
    }

    fun beitreten(code: String, bestaetigen: () -> Boolean): String? {
        try {
            val einladung = FamilienTestEinladungFormat.lesen(code) ?: return null
            if (!scope.aktiv()) return null
            val peer = FamilienTestVerbindung.verbinden(einladung, scope, wlan())
            return FamilienTestProtokoll.empfangen(peer, einladung) { aktiv() && bestaetigen() && aktiv() }
                ?.takeIf { aktiv() }
        } finally { close() }
    }

    fun wlanUeberwachen(cm: android.net.ConnectivityManager) {
        check(bindung == null)
        val netz = FamilienTestWlan.erfassen(cm)
        bindung = netz
        scope.registrieren(FamilienTestWlan.beobachten(netz, this))
        netz.pruefen()
    }

    fun aktiv(): Boolean = scope.aktiv() && (bindung?.gueltig() == true)

    override fun close() { sitzung.beenden(); scope.close() }
}
