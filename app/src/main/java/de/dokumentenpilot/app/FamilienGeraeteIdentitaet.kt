package de.dokumentenpilot.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Persistent signing identity for the synthetic family pilot, never used for archive encryption. */
internal class FamilienGeraeteIdentitaet private constructor(
    private val privateKey: java.security.PrivateKey,
    private val publicKeyValue: java.security.PublicKey
) {
    val publicKey: ByteArray
        get() = publicKeyValue.encoded.copyOf()

    val fingerprint: String
        get() = MessageDigest.getInstance("SHA-256").digest(publicKey)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Caller must supply a fresh, role-bound authenticated session transcript. */
    fun signiere(transcript: ByteArray): ByteArray {
        require(transcript.isNotEmpty() && transcript.size <= 16384)
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(DOMAIN)
            update(transcript)
            sign()
        }
    }

    companion object {
        private const val ALIAS = "familien_geraete_identitaet_v1"
        private val DOMAIN = "DokumentenPilot/FamilienPilot/Identity/v1\u0000".toByteArray(Charsets.UTF_8)

        @Synchronized
        fun laden(): FamilienGeraeteIdentitaet {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(ALIAS)) {
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                    initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build())
                    generateKeyPair()
                }
            }
            val entry = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: error("Device identity unavailable")
            return FamilienGeraeteIdentitaet(entry.privateKey, entry.certificate.publicKey)
        }

        @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
        fun nurTransporttest(): FamilienGeraeteIdentitaet {
            val pair = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            return FamilienGeraeteIdentitaet(pair.private, pair.public)
        }

        fun pruefen(publicKey: ByteArray, transcript: ByteArray, signature: ByteArray): Boolean {
            if (publicKey.size !in 1..256 || transcript.size !in 1..16384 || signature.size !in 1..80) return false
            return try {
                val key = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKey)) as java.security.interfaces.ECPublicKey
                val params = java.security.AlgorithmParameters.getInstance("EC").apply {
                    init(ECGenParameterSpec("secp256r1"))
                }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
                if (key.params.curve != params.curve || key.params.generator != params.generator ||
                    key.params.order != params.order || key.params.cofactor != params.cofactor) return false
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(DOMAIN)
                    update(transcript)
                    verify(signature)
                }
            } catch (_: Exception) { false }
        }
    }
}
