package de.dokumentenpilot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.os.IBinder
import android.os.SystemClock

internal class FamilienHintergrundRechte(
    private val prefs: android.content.SharedPreferences, private val vertrauen: FamilienGeraeteVertrauen
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("familien_hintergrund_v1", Context.MODE_PRIVATE),
        FamilienGeraeteVertrauen(context)
    )

    fun dauer(peer: String): FamilienFreigabedauer =
        if (vertrauen.status(peer) == FamilienGeraeteVertrauen.Status.BESTAETIGT)
            FamilienFreigabedauer.ausEinstellung(prefs.all[peer])
        else FamilienFreigabedauer.AUS

    fun erlaubt(peer: String) = dauer(peer) != FamilienFreigabedauer.AUS

    fun speichern(peer: String, dauer: FamilienFreigabedauer): Boolean =
        vertrauen.status(peer) == FamilienGeraeteVertrauen.Status.BESTAETIGT &&
            prefs.edit().putString(peer, dauer.name).commit()

}

/** Explicitly started, read-only local sharing. Never unlocks or retains an Activity. */
class FamilienHintergrundDienst : Service() {
    @Volatile private var pilot: FamilienTestPilot? = null
    private val laufzeit = FamilienHintergrundLaufzeit(SystemClock::elapsedRealtime)
    private val rechte by lazy { FamilienHintergrundRechte(this) }
    private val signal = java.util.concurrent.Semaphore(0)
    @Volatile private var gestartet = false
    @Volatile private var aktiverPeer: String? = null

    private fun aufwecken() {
        if (signal.availablePermits() == 0) signal.release()
    }

    private val ablauf = Runnable { ablaufPlanen() }
    private fun ablaufPlanen() {
        timer.removeCallbacks(ablauf)
        aufwecken()
        val aktive = laufzeit.aktive(rechte::erlaubt)
        if (aktive.isEmpty()) abbrechen()
        else {
            if (aktiverPeer?.let { it !in aktive } == true) pilot?.close()
            aktive.mapNotNull(laufzeit::rest).minOrNull()?.let {
                timer.postDelayed(ablauf, it.coerceAtLeast(1L))
            }
        }
    }
    private val timer = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOPP) {
            abbrechen()
            return START_NOT_STICKY
        }
        if (intent?.action != START || applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            abbrechen()
            return START_NOT_STICKY
        }
        val peer = intent.getStringExtra(GERAET)
        val manager = getSystemService(NotificationManager::class.java)
        if (peer == null || !rechte.erlaubt(peer) || !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(KANAL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            abbrechen()
            return START_NOT_STICKY
        }
        manager.createNotificationChannel(NotificationChannel(KANAL, "Familienarchiv im WLAN", NotificationManager.IMPORTANCE_LOW))
        val cm = getSystemService(ConnectivityManager::class.java)
        val netz = runCatching { FamilienTestWlan.erfassen(cm) }.getOrNull()
        if (netz == null) {
            if (!gestartet) abbrechen()
            return START_NOT_STICKY
        }
        if (gestartet && ursprungsNetz != netz.netz) return START_NOT_STICKY
        startForeground(87, benachrichtigung(wartet = false))
        laufzeit.aktivieren(peer, rechte.dauer(peer))
        if (laufzeit.aktive(rechte::erlaubt).isEmpty()) {
            abbrechen()
            return START_NOT_STICKY
        }
        if (!gestartet) {
            ursprungsNetz = netz.netz
            gestartet = true
            instanz = this
            Thread({ bereitstellen(cm, netz.netz) }, "Familienarchiv-WLAN").start()
        }
        ablaufPlanen()
        return START_NOT_STICKY
    }

    private fun benachrichtigung(wartet: Boolean): Notification {
        val ende = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOPP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (wartet) "Familienarchiv wartet auf das ursprüngliche WLAN" else "Familienarchiv im WLAN verfügbar")
            .setContentText("Bestätigte, freigegebene Geräte können Dokumente lesen.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Alle Freigaben beenden", ende).build())
            .build()
    }

    @Volatile private var ursprungsNetz: android.net.Network? = null

    private fun bereitstellen(cm: ConnectivityManager, ursprung: android.net.Network) {
        val vertrauen = FamilienGeraeteVertrauen(this)
        val freigaben = FamilienArchivFreigaben(this)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = aufwecken()
            override fun onLost(network: android.net.Network) = aufwecken()
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) = aufwecken()
            override fun onLinkPropertiesChanged(network: android.net.Network, properties: android.net.LinkProperties) = aufwecken()
        }
        val manager = getSystemService(NotificationManager::class.java)
        fun aktiv() = gestartet && laufzeit.aktive(rechte::erlaubt).isNotEmpty() &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(KANAL)?.importance != NotificationManager.IMPORTANCE_NONE
        var beobachtet = false
        var wartet = false
        try {
            val owner = FamilienGeraeteIdentitaet.laden().fingerprint
            cm.registerDefaultNetworkCallback(callback)
            beobachtet = true
            while (aktiv()) {
                val netz = runCatching { FamilienTestWlan.erfassen(cm) }.getOrNull()
                if (netz?.netz != ursprung) {
                    if (!wartet) {
                        wartet = true
                        manager.notify(87, benachrichtigung(wartet = true))
                    }
                    signal.acquire()
                    continue
                }
                if (wartet) {
                    wartet = false
                    manager.notify(87, benachrichtigung(wartet = false))
                }
                val aktuell = FamilienTestPilot(this)
                pilot = aktuell
                try {
                    if (!aktiv()) break
                    if (runCatching { aktuell.wlanUeberwachen(cm) }.isFailure || !netz.gueltig()) continue
                    fun erlaubt(peer: String) = gestartet && aktuell.aktiv() && netz.gueltig() &&
                        rechte.erlaubt(peer) && laufzeit.rest(peer) != 0L && manager.areNotificationsEnabled() &&
                        manager.getNotificationChannel(KANAL)?.importance != NotificationManager.IMPORTANCE_NONE
                    fun katalog() = FamilienArchivDaten.vomDatentraeger(this, owner)
                    fun zugriff(peer: String) = FamilienArchivZugriff(owner,
                        bestaetigt = { vertrauen.status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT },
                        aktiv = { erlaubt(peer) }, katalog = { katalog().map { it.dokument } },
                        freigaben = { freigaben.lesen(owner, peer)?.freigaben.orEmpty() },
                        privat = { freigaben.lesen(owner, peer)?.privat.orEmpty() },
                        lesen = { FamilienArchivDaten.entschluesseln(this, it) })
                    val archiv = FamilienArchivAustausch(
                        katalog = { peer ->
                            if (!erlaubt(peer)) emptyList() else freigaben.lesen(owner, peer)?.let {
                                FamilienArchivDaten.neuesAngebot(peer, katalog(), emptyList(), it, bearbeiten = false)
                            }.orEmpty()
                        },
                        lesen = { peer, id, liefern -> zugriff(peer).ansehen(peer, id, liefern) },
                        darfAnsehen = { peer, id -> zugriff(peer).darfAnsehen(peer, id) },
                        auswaehlen = { _, _, _, _ -> null },
                        empfangen = { _, _, _ -> }
                    )
                    aktuell.archivBereitstellen(getSystemService(NsdManager::class.java), archiv, hintergrund = true,
                        zugelassen = ::erlaubt) { peer ->
                        aktiverPeer = peer
                        getSharedPreferences("familien_letzte_verbindung", MODE_PRIVATE).edit()
                            .putLong(peer, System.currentTimeMillis()).apply()
                    }
                } finally {
                    aktuell.close()
                    pilot = null
                    aktiverPeer = null
                }
            }
        } catch (_: Exception) {
            // Failed network, key or storage checks end the service without exposing data.
        } finally {
            if (beobachtet) runCatching { cm.unregisterNetworkCallback(callback) }
            stopSelf()
        }
    }

    private fun abbrechen() {
        gestartet = false
        laufzeit.alleBeenden()
        pilot?.close()
        aufwecken()
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) = abbrechen()

    override fun onDestroy() {
        timer.removeCallbacksAndMessages(null)
        gestartet = false
        laufzeit.alleBeenden()
        pilot?.close()
        aufwecken()
        if (instanz === this) instanz = null
        super.onDestroy()
    }

    companion object {
        private const val GERAET = "geraet"
        internal const val KANAL = "familien_archiv_wlan"
        private const val START = "de.dokumentenpilot.app.FAMILIEN_HINTERGRUND_START"
        private const val STOPP = "de.dokumentenpilot.app.FAMILIEN_HINTERGRUND_STOPP"
        @Volatile private var instanz: FamilienHintergrundDienst? = null

        internal fun netzErlaubt(netz: android.net.Network) = instanz?.ursprungsNetz?.let { it == netz } ?: true
        internal fun rest(peer: String): Long? {
            val dienst = instanz ?: return 0L
            return if (dienst.rechte.erlaubt(peer)) dienst.laufzeit.rest(peer) else 0L
        }
        internal fun starten(context: Context, peer: String) = context.startForegroundService(
            Intent(context, FamilienHintergrundDienst::class.java).setAction(START).putExtra(GERAET, peer))
        internal fun dauerAendern(peer: String, neu: FamilienFreigabedauer) {
            instanz?.let {
                it.laufzeit.aendern(peer, neu)
                it.ablaufPlanen()
            }
        }
        internal fun geraetBeenden(peer: String) {
            instanz?.let {
                it.laufzeit.beenden(peer)
                it.ablaufPlanen()
            }
        }
        internal fun sitzungenBeenden() {
            instanz?.let {
                it.pilot?.close()
                it.ablaufPlanen()
            }
        }
        internal fun beenden(context: Context) {
            instanz?.abbrechen()
            context.stopService(Intent(context, FamilienHintergrundDienst::class.java))
        }
    }
}
