package de.dokumentenpilot.app

import android.os.SystemClock
import java.net.SocketTimeoutException
import java.util.Base64

/** Worker-thread controller for synthetic pilot only. Close on UI cancellation or lock. */
internal class FamilienTestPilot(context: android.content.Context) : AutoCloseable {
    @Volatile private var bindung: FamilienTestWlan.Bindung? = null
    private fun wlan(): FamilienTestWlan.Bindung = checkNotNull(bindung).also { it.pruefen() }
    private val freigaben = FamilienGeraeteFreigaben(context)
    private val vertrauen = FamilienGeraeteVertrauen(context)
    private val scope = FamilienTestLebenszyklus()
    private val sitzung = FamilienTestSitzung { SystemClock.elapsedRealtime() }
    init { scope.registrieren(AutoCloseable { sitzung.beenden() }) }

    fun einladen(adresse: String, anzeigen: (String) -> Unit, aktivieren: () -> Boolean = { true }, pdfEmpfangen: ((String, ByteArray) -> Unit)? = null, weitereAufnahme: Boolean = false, archiv: FamilienArchivAustausch? = null, bestaetigen: (String, String) -> Boolean): Boolean {
        try {
            val netz = wlan()
            check(adresse == netz.ip)
            val identitaet = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer(identitaet, netz))
            val token = sitzung.einladungsToken() ?: return false
            val code = try {
                FamilienTestEinladungFormat.kodieren(FamilienTestEinladungFormat.Einladung(
                    adresse, server.port, Base64.getUrlEncoder().withoutPadding().encodeToString(token),
                    identitaet.fingerprint))
            } finally { token.fill(0) }
            anzeigen(code)
            if (!aktivieren() || !aktiv()) return false
            while (aktiv()) {
                val peer = try { server.annehmen(scope) } catch (_: SocketTimeoutException) { continue }
                netz.pruefen()
                server.close()
                return FamilienTestProtokoll.senden(peer, sitzung, ::aktiv, vertrauen, freigegebeneIds = { fingerprint -> freigaben.sichtbareTestdokumente(fingerprint).map { it.id }.toSet() }, pdfEmpfangen = pdfEmpfangen, weitereAufnahme = weitereAufnahme, archiv = archiv) { lokal, partner -> aktiv() && bestaetigen(lokal, partner) && aktiv() }
            }
            return false
        } finally { close() }
    }

    fun beitreten(code: String, pdfEmpfangen: ((String, ByteArray) -> Unit)? = null, weitereAufnahme: Boolean = false, archiv: FamilienArchivAustausch? = null, bestaetigen: (String, String) -> Boolean): String? {
        try {
            val einladung = FamilienTestEinladungFormat.lesen(code) ?: return null
            if (!scope.aktiv()) return null
            val peer = FamilienTestVerbindung.verbinden(einladung, scope, wlan())
            return FamilienTestProtokoll.empfangen(peer, einladung, ::aktiv, vertrauen, pdfEmpfangen = pdfEmpfangen, freigegebeneIds = { fingerprint -> freigaben.sichtbareTestdokumente(fingerprint).map { it.id }.toSet() }, weitereAufnahme = weitereAufnahme, archiv = archiv) { lokal, partner -> aktiv() && bestaetigen(lokal, partner) && aktiv() }
                ?.takeIf { aktiv() }
        } finally { close() }
    }

    fun archivBereitstellen(nsd: android.net.nsd.NsdManager, archiv: FamilienArchivAustausch, verbunden: (String) -> Unit) {
        try {
            val netz = wlan()
            val identitaet = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer(identitaet, netz))
            val lokal = FamilienGeraeteIdentitaet.laden().fingerprint
            scope.registrieren(FamilienArchivWlan.anmelden(nsd, server.port, lokal, identitaet.fingerprint))
            while (aktiv()) {
                val verbindung = try { server.annehmen(scope) } catch (_: Exception) { continue }
                try {
                    runCatching { FamilienTestProtokoll.wiederverbinden(verbindung, true, null, ::aktiv, vertrauen, archiv, verbunden = verbunden) }
                } finally {
                    scope.entfernen(verbindung)
                }
            }
        } finally { close() }
    }

    fun archivOeffnen(nsd: android.net.nsd.NsdManager, geraet: String, archiv: FamilienArchivAustausch, verbunden: (String) -> Unit): Boolean {
        try {
            if (vertrauen.status(geraet) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return false
            val netz = wlan()
            val ziel = FamilienArchivWlan.suchen(nsd, geraet, netz, ::aktiv) ?: return false
            val verbindung = FamilienTestVerbindung.verbinden(ziel, scope, netz)
            return FamilienTestProtokoll.wiederverbinden(verbindung, false, geraet, ::aktiv, vertrauen, archiv, verbunden = verbunden)
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
