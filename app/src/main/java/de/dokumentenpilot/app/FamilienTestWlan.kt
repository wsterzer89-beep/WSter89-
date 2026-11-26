package de.dokumentenpilot.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import java.net.Inet4Address

internal object FamilienTestWlan {
    fun adresse(cm: ConnectivityManager, netz: Network): String? {
        val caps = cm.getNetworkCapabilities(netz) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        return cm.getLinkProperties(netz)?.linkAddresses?.map { it.address }
            ?.filterIsInstance<Inet4Address>()?.firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }

    class Bindung(val cm: ConnectivityManager, val netz: Network, val ip: String) {
        fun gueltig(): Boolean = cm.activeNetwork == netz && adresse(cm, netz) == ip
        fun pruefen() { check(gueltig()) { "WLAN gewechselt" } }
    }

    fun erfassen(cm: ConnectivityManager): Bindung {
        val netz = cm.activeNetwork ?: error("Kein WLAN")
        return Bindung(cm, netz, adresse(cm, netz) ?: error("Kein privates WLAN"))
    }

    fun beobachten(bindung: Bindung, pilot: FamilienTestPilot): AutoCloseable {
        val (cm, netz, ip) = Triple(bindung.cm, bindung.netz, bindung.ip)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { if (network == netz) pilot.close() }
            override fun onAvailable(network: Network) { if (network != netz) pilot.close() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network == netz && (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))) pilot.close()
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                if (network == netz && properties.linkAddresses.none { it.address.hostAddress == ip }) pilot.close()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        if (!bindung.gueltig()) pilot.close()
        return AutoCloseable { cm.unregisterNetworkCallback(callback) }
    }
}
