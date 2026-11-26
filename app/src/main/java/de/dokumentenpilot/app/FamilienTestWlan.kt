package de.dokumentenpilot.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import java.net.Inet4Address

internal object FamilienTestWlan {
    fun adresse(cm: ConnectivityManager, netz: Network): String? {
        if (cm.getNetworkCapabilities(netz)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        return cm.getLinkProperties(netz)?.linkAddresses?.map { it.address }
            ?.filterIsInstance<Inet4Address>()?.firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }

    fun beobachten(cm: ConnectivityManager, pilot: FamilienTestPilot): AutoCloseable {
        val netz = cm.activeNetwork ?: error("Kein WLAN")
        val ip = adresse(cm, netz) ?: error("Kein privates WLAN")
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { if (network == netz) pilot.close() }
            override fun onAvailable(network: Network) { if (network != netz) pilot.close() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network == netz && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) pilot.close()
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                if (network == netz && properties.linkAddresses.none { it.address.hostAddress == ip }) pilot.close()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        if (cm.activeNetwork != netz || adresse(cm, netz) != ip) pilot.close()
        return AutoCloseable { cm.unregisterNetworkCallback(callback) }
    }
}
