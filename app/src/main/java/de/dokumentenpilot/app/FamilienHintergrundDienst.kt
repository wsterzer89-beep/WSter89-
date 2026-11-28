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

    fun erlaubt(peer: String) = vertrauen.status(peer) == FamilienGeraeteVertrauen.Status.BESTAETIGT &&
        prefs.getBoolean(peer, false)

    fun speichern(peer: String, erlaubt: Boolean): Boolean =
        vertrauen.status(peer) == FamilienGeraeteVertrauen.Status.BESTAETIGT &&
            prefs.edit().putBoolean(peer, erlaubt).commit()

    fun irgendeinGeraet() = vertrauen.bestaetigtePeers().any(::erlaubt)
}

/** Explicitly started, read-only local sharing. Never unlocks or retains an Activity. */
class FamilienHintergrundDienst : Service() {
    @Volatile private var pilot: FamilienTestPilot? = null
    @Volatile private var aktivBis = 0L
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
        val rechte = FamilienHintergrundRechte(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (!rechte.irgendeinGeraet() || !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(KANAL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            abbrechen()
            return START_NOT_STICKY
        }
        manager.createNotificationChannel(NotificationChannel(KANAL, "Familienarchiv im WLAN", NotificationManager.IMPORTANCE_LOW))
        val ende = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOPP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Familienarchiv im WLAN verfügbar")
            .setContentText("Bestätigte, freigegebene Geräte können Dokumente lesen.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Zugriff beenden", ende).build())
            .build()
        startForeground(87, notification)
        if (aktivBis > SystemClock.elapsedRealtime()) return START_NOT_STICKY
        aktivBis = SystemClock.elapsedRealtime() + 30 * 60_000L
        instanz = this
        timer.postDelayed({
            if (instanz === this) abbrechen()
        }, 30 * 60_000L)
        Thread(::bereitstellen, "Familienarchiv-WLAN").start()
        return START_NOT_STICKY
    }

    private fun bereitstellen() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val rechte = FamilienHintergrundRechte(this)
        val vertrauen = FamilienGeraeteVertrauen(this)
        val freigaben = FamilienArchivFreigaben(this)
        try {
            val netz = FamilienTestWlan.erfassen(cm)
            val owner = FamilienGeraeteIdentitaet.laden().fingerprint
            val manager = getSystemService(NotificationManager::class.java)
            fun aktiv() = SystemClock.elapsedRealtime() < aktivBis && netz.gueltig() && rechte.irgendeinGeraet() &&
                manager.areNotificationsEnabled() && manager.getNotificationChannel(KANAL)?.importance != NotificationManager.IMPORTANCE_NONE
            while (aktiv()) {
                val aktuell = FamilienTestPilot(this)
                pilot = aktuell
                try {
                    if (!aktiv()) break
                    aktuell.wlanUeberwachen(cm)
                    fun erlaubt(peer: String) = aktiv() && aktuell.aktiv() && rechte.erlaubt(peer)
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
                        getSharedPreferences("familien_letzte_verbindung", MODE_PRIVATE).edit()
                            .putLong(peer, System.currentTimeMillis()).apply()
                    }
                } finally {
                    aktuell.close()
                    pilot = null
                }
            }
        } catch (_: Exception) {
            // Failed network, key or storage checks end the service without exposing data.
        } finally {
            stopSelf()
        }
    }

    private fun abbrechen() {
        aktivBis = 0
        pilot?.close()
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) = abbrechen()

    override fun onDestroy() {
        timer.removeCallbacksAndMessages(null)
        aktivBis = 0
        pilot?.close()
        if (instanz === this) instanz = null
        super.onDestroy()
    }

    companion object {
        private const val KANAL = "familien_archiv_wlan"
        private const val START = "de.dokumentenpilot.app.FAMILIEN_HINTERGRUND_START"
        private const val STOPP = "de.dokumentenpilot.app.FAMILIEN_HINTERGRUND_STOPP"
        @Volatile private var instanz: FamilienHintergrundDienst? = null

        internal fun laeuft() = instanz?.let { it.aktivBis > SystemClock.elapsedRealtime() } == true
        internal fun starten(context: Context) = context.startForegroundService(Intent(context, FamilienHintergrundDienst::class.java).setAction(START))
        internal fun sitzungenBeenden() { instanz?.pilot?.close() }
        internal fun beenden(context: Context) {
            instanz?.abbrechen()
            context.stopService(Intent(context, FamilienHintergrundDienst::class.java))
        }
    }
}
