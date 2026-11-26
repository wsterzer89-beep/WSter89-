package de.dokumentenpilot.app

import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Worker-thread only. No archive access. Caller owns accepted sockets and identity. */
internal class FamilienTestServer(
    identitaet: FamilienTestIdentitaet,
    adresse: String
) : AutoCloseable {
    private val server: SSLServerSocket
    val port: Int get() = server.localPort

    init {
        // Reuse the strict private IPv4 validation before binding.
        FamilienTestEinladungFormat.kodieren(FamilienTestEinladungFormat.Einladung(
            adresse, 1024, "A".repeat(43), identitaet.fingerprint))
        val socket = identitaet.context.serverSocketFactory.createServerSocket() as SSLServerSocket
        try {
            socket.enabledProtocols = arrayOf("TLSv1.3")
            socket.soTimeout = 1000
            socket.bind(InetSocketAddress(InetAddress.getByName(adresse), 0), 1)
            require(socket.localPort >= 1024)
            server = socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /** TLS authenticates the server only; token and explicit confirmation must follow. */
    fun annehmen(): SSLSocket {
        val peer = server.accept() as SSLSocket
        try {
            peer.enabledProtocols = arrayOf("TLSv1.3")
            peer.soTimeout = 5000
            peer.startHandshake()
            check(peer.session.protocol == "TLSv1.3")
            return peer
        } catch (e: Exception) {
            runCatching { peer.close() }
            throw e
        }
    }

    override fun close() { server.close() }
}
