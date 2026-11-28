package de.dokumentenpilot.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilienGeraeteIdentitaetTest {
    @Test fun identitaetBleibtBeimErneutenLadenErhalten() {
        val erste = FamilienGeraeteIdentitaet.laden()
        val zweite = FamilienGeraeteIdentitaet.laden()
        assertEquals(erste.fingerprint, zweite.fingerprint)
        assertArrayEquals(erste.publicKey, zweite.publicKey)
        assertEquals(64, erste.fingerprint.length)
    }

    @Test fun signaturBindetTestNachricht() {
        val identitaet = FamilienGeraeteIdentitaet.laden()
        val nachricht = "synthetischer Test: Rolle A, Sitzung 1".toByteArray()
        val signatur = identitaet.signiere(nachricht)
        assertTrue(FamilienGeraeteIdentitaet.pruefen(identitaet.publicKey, nachricht, signatur))
        assertFalse(FamilienGeraeteIdentitaet.pruefen(identitaet.publicKey,
            "synthetischer Test: Rolle B, Sitzung 2".toByteArray(), signatur))
        assertFalse(FamilienGeraeteIdentitaet.pruefen(byteArrayOf(1), nachricht, signatur))
        assertFalse(FamilienGeraeteIdentitaet.pruefen(identitaet.publicKey, nachricht, byteArrayOf(1)))
    }
}
