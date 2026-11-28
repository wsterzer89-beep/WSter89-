package de.dokumentenpilot.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class FamilienArchivWiederverbindungTest {
    @Test fun erneutOeffnenUndMehrerePdfsAnzeigen() {
        val geraete = TestGeraete()
        val empfangen = mutableListOf<String>()
        assertEquals(true to true, geraete.verbinden { _, _, _, _ -> null })
        assertEquals(true to true, geraete.verbinden(empfangen = empfangen) { _, katalog, _, verbunden ->
            assertTrue(verbunden())
            assertEquals(listOf("Synthetische Kategorie"), katalog.map { it.quellKategorie }.distinct())
            assertEquals(listOf("Synthetische Mappe"), katalog.first().mappen)
            val naechstes = katalog.firstOrNull { it.id !in empfangen }
            naechstes?.let { FamilienArchivAustausch.Anfrage(it.id) }
        })
        assertEquals(geraete.eintraege.map { it.id }, empfangen)
    }

    @Test fun widerrufenesGeraetErhaeltKeinenKatalog() {
        val geraete = TestGeraete()
        assertTrue(geraete.serverVertrauen.geraetWiderrufen(geraete.client.fingerprint))
        assertEquals(false to false, geraete.verbinden { _, _, _, _ -> error("Unerlaubter Katalog") })
    }

    @Test fun widerrufBeendetAktiveVorschau() {
        val geraete = TestGeraete()
        val empfangen = mutableListOf<String>()
        val result = geraete.verbinden(empfangen = empfangen, beiVorschau = { verlaengern ->
            assertTrue(geraete.serverVertrauen.geraetWiderrufen(geraete.client.fingerprint))
            assertFalse(runCatching { verlaengern() }.getOrDefault(false))
        }) { _, katalog, _, _ -> FamilienArchivAustausch.Anfrage(katalog.first().id) }
        assertFalse(result.first)
        assertFalse(result.second)
    }

    @Test fun abweichenderEigentuemerErhaeltKeineFreigabe() {
        val geraete = TestGeraete()
        assertEquals(false to false, geraete.verbinden(erwartet = "0".repeat(64)) { _, _, _, _ ->
            error("Unerwarteter Katalog")
        })
    }

    @Test fun hintergrundZugriffStandardmaessigAusUndBeiWiderrufGesperrt() {
        val geraete = TestGeraete()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("hintergrund_instrumentation", 0)
        assertTrue(prefs.edit().clear().commit())
        val rechte = FamilienHintergrundRechte(prefs, geraete.serverVertrauen)
        val peer = geraete.client.fingerprint
        assertFalse(rechte.erlaubt(peer))
        assertEquals(false to false, geraete.verbinden(zugelassen = rechte::erlaubt) { _, _, _, _ ->
            error("Ohne Freigabe darf kein Katalog gesendet werden")
        })
        assertTrue(rechte.speichern(peer, true))
        assertEquals(true to true, geraete.verbinden(zugelassen = rechte::erlaubt) { _, katalog, _, _ ->
            assertEquals(2, katalog.size)
            null
        })
        assertTrue(rechte.speichern(peer, false))
        assertFalse(rechte.erlaubt(peer))
        assertEquals(false to false, geraete.verbinden(zugelassen = rechte::erlaubt) { _, _, _, _ ->
            error("Nach Widerruf darf kein Katalog gesendet werden")
        })
    }

    private class TestGeraete {
        val client = FamilienGeraeteIdentitaet.nurTransporttest()
        private val server = FamilienGeraeteIdentitaet.nurTransporttest()
        val serverVertrauen = vertrauen("server", client.fingerprint)
        private val clientVertrauen = vertrauen("client", server.fingerprint)
        val eintraege = listOf(
            FamilienArchivAustausch.Eintrag("fca27388-6161-4c35-a4c0-701bee636cf7.dp", "Synthetisch A", true, true,
                quellKategorie = "Synthetische Kategorie", mappen = listOf("Synthetische Mappe")),
            FamilienArchivAustausch.Eintrag("fca27388-6161-4c35-a4c0-701bee636cf8.dp", "Synthetisch B", true, false,
                quellKategorie = "Synthetische Kategorie")
        )

        private fun vertrauen(rolle: String, peer: String): FamilienGeraeteVertrauen {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("reconnect_instrumentation_$rolle", 0)
            assertTrue(prefs.edit().clear().commit())
            return FamilienGeraeteVertrauen.nurTest(prefs).also { assertTrue(it.bestaetigen(peer)) }
        }

        fun verbinden(
            empfangen: MutableList<String> = mutableListOf(),
            erwartet: String = server.fingerprint,
            beiVorschau: ((() -> Boolean) -> Unit)? = null,
            zugelassen: (String) -> Boolean = { true },
            auswahl: (String, List<FamilienArchivAustausch.Eintrag>, List<String>, () -> Boolean) -> FamilienArchivAustausch.Anfrage?
        ): Pair<Boolean, Boolean> {
            val executor = Executors.newSingleThreadExecutor()
            try {
                FamilienTestIdentitaet.erstellen().use { tls ->
                    val loopback = InetAddress.getLoopbackAddress()
                    (tls.context.serverSocketFactory.createServerSocket(0, 1, loopback) as SSLServerSocket).use { listener ->
                        listener.enabledProtocols = arrayOf("TLSv1.3")
                        listener.soTimeout = 5_000
                        val serverResult = executor.submit<Boolean> {
                            (listener.accept() as SSLSocket).use { peer ->
                                peer.soTimeout = 5_000
                                peer.startHandshake()
                                val archiv = FamilienArchivAustausch(
                                    katalog = { eintraege },
                                    lesen = { _, id, liefern ->
                                        val dokument = checkNotNull(FamilienTestPdf.erstellen("demo-1"))
                                        try { liefern(dokument) } finally { dokument.fill(0) }
                                        eintraege.any { it.id == id }
                                    },
                                    darfAnsehen = { _, id -> eintraege.any { it.id == id } },
                                    auswaehlen = { _, _, _, _ -> error("Eigentümer zeigt keine Liste") },
                                    empfangen = { _, _, _ -> error("Eigentümer empfängt kein PDF") }
                                )
                                FamilienTestProtokoll.wiederverbinden(peer, true, null, { true }, serverVertrauen, archiv, server, zugelassen = zugelassen)
                            }
                        }
                        val context = SSLContext.getInstance("TLSv1.3").apply {
                            init(null, arrayOf(FamilienTestTls(tls.fingerprint)), null)
                        }
                        (context.socketFactory.createSocket(loopback, listener.localPort) as SSLSocket).use { peer ->
                            peer.enabledProtocols = arrayOf("TLSv1.3")
                            peer.soTimeout = 5_000
                            peer.startHandshake()
                            val archiv = FamilienArchivAustausch(
                                katalog = { error("Anfragendes Gerät liefert keinen Katalog") },
                                lesen = { _, _, _ -> false },
                                darfAnsehen = { _, _ -> false },
                                auswaehlen = auswahl,
                                empfangen = { dokument, bytes, verlaengern ->
                                    assertFalse(bytes.isEmpty())
                                    assertTrue(verlaengern())
                                    empfangen.add(dokument.id)
                                    beiVorschau?.invoke(verlaengern)
                                }
                            )
                            val clientResult = try {
                                FamilienTestProtokoll.wiederverbinden(peer, false, erwartet,
                                    { true }, clientVertrauen, archiv, client)
                            } catch (fehler: java.io.IOException) {
                                if (beiVorschau == null) throw fehler
                                false
                            }
                            return serverResult.get(10, TimeUnit.SECONDS) to clientResult
                        }
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
