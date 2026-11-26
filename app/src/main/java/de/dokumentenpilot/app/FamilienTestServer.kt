package de.dokumentenpilot.app

import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Worker-thread only. No archive access. Caller owns accepted sockets and identity. */
internal class FamilienTestServer private constructor(
    identitaet: FamilienTestIdentitaet,
    adresse: String,
    private val bindung: FamilienTestWlan.Bindung?
) : AutoCloseable {
    constructor(identitaet: FamilienTestIdentitaet, bindung: FamilienTestWlan.Bindung) : this(identitaet, bindung.ip, bindung)

    companion object {
        @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
        fun nurTransporttest(identitaet: FamilienTestIdentitaet, adresse: String) = FamilienTestServer(identitaet, adresse, null)
    }

    private val server: SSLServerSocket
    val port: Int get() = server.localPort

    init {
        // Reuse the strict private IPv4 validation before binding.
        FamilienTestEinladungFormat.kodieren(FamilienTestEinladungFormat.Einladung(
            adresse, 1024, "A".repeat(43), identitaet.fingerprint))
        val socket = identitaet.context.serverSocketFactory.createServerSocket() as SSLServerSocket
        try {
            socket.useClientMode = false
            socket.enabledProtocols = arrayOf("TLSv1.3")
            socket.soTimeout = 1000
            bindung?.pruefen()
            socket.bind(InetSocketAddress(InetAddress.getByName(adresse), 0), 1)
            require(socket.localPort >= 1024)
            server = socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /** TLS authenticates the server only; token and explicit confirmation must follow. */
    fun annehmen(scope: FamilienTestLebenszyklus): SSLSocket {
        val peer = scope.registrieren(server.accept() as SSLSocket)
        try {
            bindung?.peerPruefen(peer.inetAddress)
            peer.useClientMode = false
            peer.enabledProtocols = arrayOf("TLSv1.3")
            peer.soTimeout = 5000
            peer.startHandshake()
            check(peer.session.protocol == "TLSv1.3")
            bindung?.pruefen()
            return peer
        } catch (e: Exception) {
            runCatching { peer.close() }
            throw e
        }
    }

    override fun close() { server.close() }
}
