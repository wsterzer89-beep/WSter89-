package de.dokumentenpilot.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.NetworkInterface

@RunWith(AndroidJUnit4::class)
class FamilienTestTransportTest {
    @Test fun verschluesselteUebertragung() {
        val ip = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { java.util.Collections.list(it.inetAddresses) }
            .filterIsInstance<java.net.Inet4Address>()
            .first { it.isSiteLocalAddress }.hostAddress!!
        val scope = FamilienTestLebenszyklus()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val identity = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer(identity, ip))
            val session = FamilienTestSitzung { android.os.SystemClock.elapsedRealtime() }
            val invitation = FamilienTestEinladungFormat.Einladung(ip, server.port,
                Base64.getUrlEncoder().withoutPadding().encodeToString(session.einladungsToken()!!),
                identity.fingerprint)
            val sender = executor.submit<Boolean> {
                FamilienTestProtokoll.senden(server.annehmen(scope), session) { true }
            }
            val peer = try {
                FamilienTestVerbindung.verbinden(invitation, scope)
            } catch (clientError: Exception) {
                try {
                    sender.get(7, TimeUnit.SECONDS)
                } catch (serverError: java.util.concurrent.ExecutionException) {
                    throw AssertionError("Server handshake failed", serverError.cause)
                } catch (serverError: Exception) {
                    clientError.addSuppressed(serverError)
                }
                throw clientError
            }
            assertEquals("DokumentenPilot: synthetisches Testdokument, keine privaten Daten.",
                FamilienTestProtokoll.empfangen(peer, invitation) { true })
            assertTrue(sender.get(10, TimeUnit.SECONDS))
            assertNull(session.testdaten())
        } finally {
            scope.close()
            executor.shutdownNow()
        }
    }

    @Test fun falscherFingerabdruck() {
        val identity = FamilienTestIdentitaet.erstellen()
        try {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val cert = java.util.Collections.list(store.aliases())
                .filter { it.startsWith("familien_pilot_") }
                .map { store.getCertificate(it) as java.security.cert.X509Certificate }
                .first { java.security.MessageDigest.getInstance("SHA-256").digest(it.encoded)
                    .joinToString("") { b -> "%02x".format(b.toInt() and 255) } == identity.fingerprint }
            FamilienTestTls(identity.fingerprint).checkServerTrusted(arrayOf(cert), "EC")
            try {
                FamilienTestTls("0".repeat(64)).checkServerTrusted(arrayOf(cert), "EC")
                fail("Wrong certificate pin accepted")
            } catch (_: java.security.cert.CertificateException) { }
        } finally { identity.close() }
    }
}
