package de.dokumentenpilot.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import java.net.Inet4Address

internal object FamilienTestWlan {
    internal fun unveraendert(gleichesNetz: Boolean, wlan: Boolean, vpn: Boolean, ursprungsIp: String, aktuelleIp: String?): Boolean =
        gleichesNetz && wlan && !vpn && ursprungsIp == aktuelleIp

    fun adresse(cm: ConnectivityManager, netz: Network): String? {
        val caps = cm.getNetworkCapabilities(netz) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        return cm.getLinkProperties(netz)?.linkAddresses?.map { it.address }
            ?.filterIsInstance<Inet4Address>()?.firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }

    class Bindung(val cm: ConnectivityManager, val netz: Network, val ip: String) {
        fun gueltig(): Boolean {
            val caps = cm.getNetworkCapabilities(netz)
            return unveraendert(cm.activeNetwork == netz,
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true, ip, adresse(cm, netz))
        }
        fun pruefen() { check(gueltig()) { "WLAN gewechselt" } }
        fun peerPruefen(peer: java.net.InetAddress) {
            pruefen()
            require(peer is Inet4Address && peer.isSiteLocalAddress && peer.hostAddress != ip)
            val links = cm.getLinkProperties(netz) ?: error("WLAN verloren")
            require(links.routes.any { (it.gateway == null || it.gateway?.isAnyLocalAddress == true) && it.matches(peer) }) { "Gegenstelle nicht direkt im WLAN" }
            pruefen()
        }
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
                if (network == netz && (properties.linkAddresses.none { it.address.hostAddress == ip } || !bindung.gueltig())) pilot.close()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        if (!bindung.gueltig()) pilot.close()
        return AutoCloseable { cm.unregisterNetworkCallback(callback) }
    }
}
