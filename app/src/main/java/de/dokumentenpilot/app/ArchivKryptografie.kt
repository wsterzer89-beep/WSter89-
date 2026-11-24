package de.dokumentenpilot.app

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Existing archive wire format: one IV length byte, 12-byte IV, ciphertext and 16-byte tag. */
internal object ArchivKryptografie {
    fun verschluesseln(daten: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == 12)
        return byteArrayOf(12) + cipher.iv + cipher.doFinal(daten)
    }

    fun entschluesseln(daten: ByteArray, key: SecretKey): ByteArray {
        require(daten.size >= 29 && daten[0].toInt() == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, daten.copyOfRange(1, 13)))
        return cipher.doFinal(daten, 13, daten.size - 13)
    }
}
