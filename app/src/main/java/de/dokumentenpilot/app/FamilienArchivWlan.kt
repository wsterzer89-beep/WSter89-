package de.dokumentenpilot.app

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Discovery offers addresses only. Trust always comes from the signed TLS transcript. */
internal object FamilienArchivWlan {
    private const val TYP = "_dp-archiv._tcp."
    private val fingerprint = Regex("[0-9a-f]{64}")

    fun anmelden(nsd: NsdManager, port: Int, geraet: String, pin: String): AutoCloseable {
        val info = NsdServiceInfo().apply {
            serviceName = "dp-" + UUID.randomUUID()
            serviceType = TYP
            setPort(port)
            setAttribute("geraet", geraet)
            setAttribute("tls", pin)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return AutoCloseable { runCatching { nsd.unregisterService(listener) } }
    }

    @Suppress("DEPRECATION")
    fun suchen(
        nsd: NsdManager, geraet: String, netz: FamilienTestWlan.Bindung, aktiv: () -> Boolean
    ): FamilienTestEinladungFormat.Einladung? {
        val dienste = ArrayBlockingQueue<NsdServiceInfo>(32)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { dienste.offer(info) }
            override fun onServiceLost(info: NsdServiceInfo) { dienste.remove(info) }
        }
        nsd.discoverServices(TYP, NsdManager.PROTOCOL_DNS_SD, listener)
        val ende = SystemClock.elapsedRealtime() + 10_000L
        try {
            while (aktiv() && SystemClock.elapsedRealtime() < ende) {
                val dienst = dienste.poll(350, TimeUnit.MILLISECONDS) ?: continue
                val antwort = java.util.concurrent.CompletableFuture<NsdServiceInfo?>()
                nsd.resolveService(dienst, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) { antwort.complete(null) }
                    override fun onServiceResolved(info: NsdServiceInfo) { antwort.complete(info) }
                })
                val info = try { antwort.get(2, TimeUnit.SECONDS) }
                           catch (_: java.util.concurrent.TimeoutException) { continue }
                           ?: continue
                if (info.attributes["geraet"]?.toString(Charsets.US_ASCII) != geraet) continue
                val pin = info.attributes["tls"]?.toString(Charsets.US_ASCII) ?: continue
                val hosts = if (android.os.Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
                val host = hosts.firstOrNull { runCatching { netz.peerPruefen(it) }.isSuccess } ?: continue
                if (!fingerprint.matches(pin) || info.port !in 1024..65535 ||
                    !aktiv() || runCatching { netz.peerPruefen(host) }.isFailure) continue
                // The reconnect protocol does not use an invitation token.
                return FamilienTestEinladungFormat.Einladung(host.hostAddress ?: continue, info.port, "A".repeat(43), pin)
            }
            return null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
