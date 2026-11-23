package de.dokumentenpilot.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object DokumentKrypto {
    // Unverändert: Schlüssel für bestehende Dokumente
    private val schluesselName = "DokumentenPilotSchluessel"

    @Synchronized
    private fun schluesselHolen(anlegen: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(
            "AndroidKeyStore"
        )

        keyStore.load(null)

        val vorhanden = keyStore.getKey(
            schluesselName,
            null
        ) as? SecretKey

        if (vorhanden != null) {
            return vorhanden
        }

        check(anlegen) { "Der vorhandene Archivschlüssel ist nicht verfügbar" }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val parameter = KeyGenParameterSpec.Builder(
            schluesselName,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(
                KeyProperties.BLOCK_MODE_GCM
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .build()

        generator.init(parameter)

        return generator.generateKey()
    }

    fun archivSchluesselPruefen() {
        schluesselHolen(anlegen = false)
    }

    fun datenVerschluesseln(
        daten: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            schluesselHolen(anlegen = true)
        )

        return byteArrayOf(cipher.iv.size.toByte()) +
            cipher.iv +
            cipher.doFinal(daten)
    }

    fun datenEntschluesseln(
        daten: ByteArray
    ): ByteArray {
        require(daten.isNotEmpty())

        val ivLaenge = daten[0].toInt() and 0xFF

        require(ivLaenge == 12)
        require(daten.size > ivLaenge + 1)

        val iv = daten.copyOfRange(
            1,
            1 + ivLaenge
        )

        val verschluesselt = daten.copyOfRange(
            1 + ivLaenge,
            daten.size
        )

        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.DECRYPT_MODE,
            schluesselHolen(anlegen = false),
            GCMParameterSpec(128, iv)
        )

        return cipher.doFinal(verschluesselt)
    }

}
