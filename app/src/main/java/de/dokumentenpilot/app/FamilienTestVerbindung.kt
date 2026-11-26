package de.dokumentenpilot.app

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Blocking pilot transport; invoke only on a worker thread after explicit user consent. */
internal object FamilienTestVerbindung {
    fun verbinden(einladung: FamilienTestEinladungFormat.Einladung, scope: FamilienTestLebenszyklus): SSLSocket {
        // Revalidate even when the caller constructed the data class directly.
        FamilienTestEinladungFormat.kodieren(einladung)
        val context = SSLContext.getInstance("TLSv1.3")
        context.init(null, arrayOf(FamilienTestTls(einladung.fingerprint)), null)
        val raw = scope.registrieren(Socket())
        var tls: SSLSocket? = null
        try {
            raw.connect(InetSocketAddress(einladung.host, einladung.port), 5000)
            raw.soTimeout = 5000
            val socket = context.socketFactory.createSocket(raw, einladung.host, einladung.port, true) as SSLSocket
            tls = socket
            scope.registrieren(socket)
            socket.useClientMode = true
            socket.enabledProtocols = arrayOf("TLSv1.3")
            socket.soTimeout = 5000
            socket.startHandshake()
            check(socket.session.protocol == "TLSv1.3")
            return socket
        } catch (e: Exception) {
            runCatching { tls?.close() }
            runCatching { raw.close() }
            throw e
        }
    }
}
