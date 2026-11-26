package de.dokumentenpilot.app

import java.security.SecureRandom
import java.security.MessageDigest

/** Isolated pilot gate. Transport must authenticate the peer before calling this class. */
internal class FamilienTestSitzung(private val jetzt: () -> Long) {
    private val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val start = jetzt()
    private var zustand = Zustand.OFFEN
    private enum class Zustand { OFFEN, ANGENOMMEN, BESTAETIGT, BEENDET }

    @Synchronized
    fun einladungsToken(): ByteArray? =
        if (zustand == Zustand.OFFEN && gueltig()) token.copyOf() else null

    private fun gueltig(): Boolean {
        val dauer = jetzt() - start
        return dauer >= 0 && dauer < 600_000
    }

    @Synchronized
    fun annehmen(kandidat: ByteArray): Boolean {
        if (zustand != Zustand.OFFEN || !gueltig() ||
            kandidat.size != token.size || !MessageDigest.isEqual(token, kandidat)) return false
        zustand = Zustand.ANGENOMMEN
        token.fill(0)
        return true
    }

    @Synchronized
    fun bestaetigen(): Boolean {
        if (zustand != Zustand.ANGENOMMEN || !gueltig()) return false
        zustand = Zustand.BESTAETIGT
        return true
    }

    @Synchronized
    fun testdaten(): ByteArray? =
        if (zustand == Zustand.BESTAETIGT && gueltig())
            "DokumentenPilot: synthetisches Testdokument, keine privaten Daten.".toByteArray(Charsets.UTF_8)
        else null

    @Synchronized
    fun beenden() {
        zustand = Zustand.BEENDET
        token.fill(0)
    }
}
