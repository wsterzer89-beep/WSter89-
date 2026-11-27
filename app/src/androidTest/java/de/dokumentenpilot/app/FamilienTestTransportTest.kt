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
    @Test fun lokaleTestrechteBleibenUndWerdenWiderrufen() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val profile = FamilienTestProfile(context)
        val ansehen = FamilienBerechtigungen.Aktion.ANSEHEN
        val suchen = FamilienBerechtigungen.Aktion.SUCHEN
        try {
            assertTrue(profile.entfernen())
            assertFalse(profile.rechteSpeichern("demo-1", setOf(ansehen)))
            assertTrue(profile.speichern())
            assertTrue(profile.rechteSpeichern("demo-1", setOf(ansehen, suchen)))
            assertTrue(profile.rechteSpeichern("demo-2", setOf(ansehen)))
            assertFalse(profile.rechteSpeichern("real-document", setOf(ansehen)))
            assertFalse(profile.rechteSpeichern("demo-1", setOf(FamilienBerechtigungen.Aktion.EINLADEN)))
            val erneut = FamilienTestProfile(context)
            assertTrue(erneut.vorhanden())
            assertEquals(setOf(ansehen, suchen), erneut.freigaben().single { it.bereichId == "demo-1" }.erlaubteAktionen)
            assertTrue(erneut.rechteSpeichern("demo-1", emptySet()))
            assertEquals(listOf("demo-2"), FamilienTestProfile(context).freigaben().map { it.bereichId })
            assertTrue(erneut.entfernen())
            assertFalse(FamilienTestProfile(context).vorhanden())
            assertTrue(FamilienTestProfile(context).freigaben().isEmpty())
            assertTrue(erneut.speichern())
            assertTrue(erneut.freigaben().isEmpty())
        } finally {
            assertTrue(profile.entfernen())
        }
    }

    @Test fun verschluesselteUebertragung() = vertrauensTransfer(false, false)

    @Test fun widerrufBlockiertUebertragung() = vertrauensTransfer(true, false)

    @Test fun fremderSchluesselBlockiertUebertragung() = vertrauensTransfer(false, true)

    @Test fun neueKopplungSpeichertBeideGeraete() = vertrauensTransfer(false, false, neu = true)

    @Test fun abgelehnteNeueKopplungSpeichertNichts() = vertrauensTransfer(false, false, neu = true, zustimmung = false)

    @Test fun nurFreigegebeneKennungenEmpfangen() = vertrauensTransfer(false, false, ids = setOf("demo-1"))

    @Test fun unbekannteKennungBlockiertTransfer() = vertrauensTransfer(false, false, ids = setOf("real-document"))

    private fun vertrauensTransfer(widerrufen: Boolean, fremd: Boolean, neu: Boolean = false, zustimmung: Boolean = true, ids: Set<String> = emptySet()) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("familien_geraete_vertrauen_instrumentation", android.content.Context.MODE_PRIVATE)
        assertTrue("Isolierte Testeinstellungen konnten nicht geleert werden", prefs.edit().clear().commit())
        val trust = FamilienGeraeteVertrauen.nurTest(prefs)
        val senderKey = FamilienGeraeteIdentitaet.nurTransporttest()
        val receiverKey = FamilienGeraeteIdentitaet.nurTransporttest()
        val receiverPrefs = context.getSharedPreferences("familien_geraete_empfaenger_instrumentation", android.content.Context.MODE_PRIVATE)
        assertTrue(receiverPrefs.edit().clear().commit())
        val receiverTrust = FamilienGeraeteVertrauen.nurTest(receiverPrefs)
        if (!neu) assertTrue(trust.bestaetigen(if (fremd) FamilienGeraeteIdentitaet.nurTransporttest().fingerprint else receiverKey.fingerprint))
        if (widerrufen) assertTrue(trust.widerrufen())
        val ip = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { java.util.Collections.list(it.inetAddresses) }
            .filterIsInstance<java.net.Inet4Address>()
            .first { it.isSiteLocalAddress }.hostAddress!!
        val scope = FamilienTestLebenszyklus()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val identity = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer.nurTransporttest(identity, ip))
            val session = FamilienTestSitzung { android.os.SystemClock.elapsedRealtime() }
            val invitation = FamilienTestEinladungFormat.Einladung(ip, server.port,
                Base64.getUrlEncoder().withoutPadding().encodeToString(session.einladungsToken()!!),
                identity.fingerprint)
            val sender = executor.submit<Boolean> {
                runCatching { FamilienTestProtokoll.senden(server.annehmen(scope), session, vertrauen = trust, lokaleIdentitaet = senderKey, freigegebeneIds = { fingerprint ->
                    assertEquals(receiverKey.fingerprint, fingerprint)
                    ids
                }) { lokal, partner ->
                    assertEquals(senderKey.fingerprint, lokal)
                    assertEquals(receiverKey.fingerprint, partner)
                    true
                } }.getOrDefault(false)
            }
            val peer = try {
                FamilienTestVerbindung.nurTransporttest(invitation, scope)
            } catch (clientError: Exception) {
                try {
                    sender.get(7, TimeUnit.SECONDS)
                } catch (serverError: java.util.concurrent.ExecutionException) {
                    val details = generateSequence(serverError.cause) { it.cause }.take(8).joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }
                    throw AssertionError("Server handshake failed: $details", serverError.cause)
                } catch (serverError: Exception) {
                    clientError.addSuppressed(serverError)
                }
                throw clientError
            }
            val received = runCatching { FamilienTestProtokoll.empfangen(peer, invitation, vertrauen = receiverTrust, lokaleIdentitaet = receiverKey) { lokal, partner ->
                assertEquals(receiverKey.fingerprint, lokal)
                assertEquals(senderKey.fingerprint, partner)
                zustimmung
            } }
            val erfolgreich = !widerrufen && !fremd && zustimmung && ids.all { it == "demo-1" || it == "demo-2" }
            if (!erfolgreich) assertNull(received.getOrNull())
            else {
                val empfangen = received.getOrThrow()!!
                assertTrue(empfangen.startsWith("DokumentenPilot: synthetisches Testdokument, keine privaten Daten."))
                assertEquals("demo-1" in ids, empfangen.contains("Beispieldokument 1"))
                assertEquals("demo-2" in ids, empfangen.contains("Beispieldokument 2"))
            }
            assertEquals(erfolgreich, sender.get(10, TimeUnit.SECONDS))
            if (neu) {
                val expected = if (zustimmung) FamilienGeraeteVertrauen.Status.BESTAETIGT else FamilienGeraeteVertrauen.Status.NEU
                assertEquals(expected, trust.status(receiverKey.fingerprint))
                assertEquals(expected, receiverTrust.status(senderKey.fingerprint))
            }
            assertNull(session.testdaten())
        } finally {
            scope.close()
            executor.shutdownNow()
            assertTrue(receiverPrefs.edit().clear().commit())
            assertTrue("Isolierte Testeinstellungen konnten nicht geleert werden", prefs.edit().clear().commit())
        }
    }

    @Test fun empfaengerAblehnung() = ablehnung(false, true)
    @Test fun senderAblehnung() = ablehnung(true, false)

    private fun ablehnung(empfaenger: Boolean, senderFreigabe: Boolean) {
        val ip = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { java.util.Collections.list(it.inetAddresses) }
            .filterIsInstance<java.net.Inet4Address>()
            .first { it.isSiteLocalAddress }.hostAddress!!
        val scope = FamilienTestLebenszyklus()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val identity = scope.registrieren(FamilienTestIdentitaet.erstellen())
            val server = scope.registrieren(FamilienTestServer.nurTransporttest(identity, ip))
            val session = FamilienTestSitzung { android.os.SystemClock.elapsedRealtime() }
            val invitation = FamilienTestEinladungFormat.Einladung(ip, server.port,
                Base64.getUrlEncoder().withoutPadding().encodeToString(session.einladungsToken()!!),
                identity.fingerprint)
            val sender = executor.submit<Boolean> {
                runCatching { FamilienTestProtokoll.senden(server.annehmen(scope), session, lokaleIdentitaet = FamilienGeraeteIdentitaet.nurTransporttest()) { _, _ -> senderFreigabe } }.getOrDefault(false)
            }
            val peer = try {
                FamilienTestVerbindung.nurTransporttest(invitation, scope)
            } catch (clientError: Exception) {
                try {
                    sender.get(7, TimeUnit.SECONDS)
                } catch (serverError: java.util.concurrent.ExecutionException) {
                    val details = generateSequence(serverError.cause) { it.cause }.take(8).joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }
                    throw AssertionError("Server handshake failed: $details", serverError.cause)
                } catch (serverError: Exception) {
                    clientError.addSuppressed(serverError)
                }
                throw clientError
            }
            assertNull(runCatching { FamilienTestProtokoll.empfangen(peer, invitation, lokaleIdentitaet = FamilienGeraeteIdentitaet.nurTransporttest()) { _, _ -> empfaenger } }.getOrNull())
            assertFalse(sender.get(10, TimeUnit.SECONDS))
            assertNull(session.testdaten())
        } finally {
            scope.close()
            executor.shutdownNow()
        }
    }

    @Test fun tcpLoopbackKontrolle() {
        val address = java.net.InetAddress.getByName("127.0.0.1")
        val executor = Executors.newSingleThreadExecutor()
        try {
            java.net.ServerSocket(0, 1, address).use { server ->
                server.soTimeout = 10000
                val result = executor.submit<Int> {
                    server.accept().use { peer ->
                        peer.soTimeout = 10000
                        val received = peer.inputStream.read()
                        peer.outputStream.write(42)
                        peer.outputStream.flush()
                        received
                    }
                }
                java.net.Socket(address, server.localPort).use { peer ->
                    peer.soTimeout = 10000
                    peer.outputStream.write(17)
                    peer.outputStream.flush()
                    assertEquals(42, peer.inputStream.read())
                }
                assertEquals(17, result.get(12, TimeUnit.SECONDS))
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun keystoreSignatur() {
        val identity = FamilienTestIdentitaet.erstellen()
        try {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = java.util.Collections.list(store.aliases())
                .filter { it.startsWith("familien_pilot_") }
                .map { store.getEntry(it, null) as java.security.KeyStore.PrivateKeyEntry }
                .first { java.security.MessageDigest.getInstance("SHA-256").digest(it.certificate.encoded)
                    .joinToString("") { b -> "%02x".format(b.toInt() and 255) } == identity.fingerprint }
            val message = "synthetic-signature-test".toByteArray()
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initSign(entry.privateKey)
            signature.update(message)
            val signed = signature.sign()
            signature.initVerify(entry.certificate.publicKey)
            signature.update(message)
            assertTrue(signature.verify(signed))
        } finally { identity.close() }
    }

    @Test fun tlsLoopback() {
        val identity = FamilienTestIdentitaet.erstellen()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val loopback = java.net.InetAddress.getByName("127.0.0.1")
            val server = identity.context.serverSocketFactory.createServerSocket(0, 1, loopback) as javax.net.ssl.SSLServerSocket
            server.use {
                server.enabledProtocols = arrayOf("TLSv1.3")
                server.soTimeout = 10000
                val sender = executor.submit<Int> {
                    (server.accept() as javax.net.ssl.SSLSocket).use { peer ->
                        peer.soTimeout = 10000
                        peer.startHandshake()
                        peer.outputStream.write(42)
                        peer.outputStream.flush()
                        42
                    }
                }
                val context = javax.net.ssl.SSLContext.getInstance("TLSv1.3")
                context.init(null, arrayOf(FamilienTestTls(identity.fingerprint)), null)
                try {
                    (context.socketFactory.createSocket(loopback, server.localPort) as javax.net.ssl.SSLSocket).use { peer ->
                        peer.enabledProtocols = arrayOf("TLSv1.3")
                        peer.soTimeout = 10000
                        peer.startHandshake()
                        assertEquals(42, peer.inputStream.read())
                    }
                    assertEquals(42, sender.get(12, TimeUnit.SECONDS))
                } catch (e: Exception) {
                    val serverFailure = runCatching { sender.get(12, TimeUnit.SECONDS) }.exceptionOrNull()
                    val details = generateSequence(serverFailure) { it.cause }.take(8).joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }
                    throw AssertionError("Loopback client: ${e.javaClass.name}: ${e.message}; server: $details", e)
                }
            }
        } finally {
            executor.shutdownNow()
            identity.close()
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
