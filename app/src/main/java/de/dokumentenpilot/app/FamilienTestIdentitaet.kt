package de.dokumentenpilot.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

/** Ephemeral pilot identity, separate from all archive keys. Caller must close it. */
internal class FamilienTestIdentitaet private constructor(
    private val alias: String,
    val fingerprint: String,
    val context: SSLContext
) : AutoCloseable {
    override fun close() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    companion object {
        private var bereinigt = false

        @Synchronized
        fun erstellen(): FamilienTestIdentitaet {
            if (!bereinigt) {
                val alt = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val muster = Regex("familien_pilot_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                val aliases = java.util.Collections.list(alt.aliases())
                aliases.filter { muster.matches(it) }.forEach { alt.deleteEntry(it) }
                bereinigt = true
            }
            val alias = "familien_pilot_" + UUID.randomUUID()
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            try {
                val now = System.currentTimeMillis()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                    initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        // Conscrypt may hash the TLS transcript before invoking raw ECDSA.
                        // Only this short-lived pilot key permits prehashed signatures.
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                        .setCertificateSubject(X500Principal("CN=DokumentenPilot Test"))
                        .setCertificateNotBefore(Date(now - 60000))
                        .setCertificateNotAfter(Date(now + 600000))
                        .build())
                    generateKeyPair()
                }
                val entry = store.getEntry(alias, null) as KeyStore.PrivateKeyEntry
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(entry.certificate.encoded)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                // AndroidKeyStore private keys are non-exportable; use the platform factory
                // on the original store and restrict its selected aliases below.
                val factory = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
                factory.init(store, null)
                val delegate = factory.keyManagers.filterIsInstance<javax.net.ssl.X509KeyManager>().first()
                val manager = object : javax.net.ssl.X509ExtendedKeyManager() {
                    override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?) = null
                    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?) = null
                    override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?) =
                        delegate.getServerAliases(keyType, issuers)?.filter { it == alias }?.toTypedArray()?.takeIf { it.isNotEmpty() }
                    override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?) =
                        getServerAliases(keyType, issuers)?.firstOrNull()
                    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, engine: javax.net.ssl.SSLEngine?) =
                        getServerAliases(keyType, issuers)?.firstOrNull()
                    override fun getCertificateChain(requested: String?) =
                        if (requested == alias) delegate.getCertificateChain(requested) else null
                    override fun getPrivateKey(requested: String?) = if (requested == alias) delegate.getPrivateKey(requested) else null
                }
                val context = SSLContext.getInstance("TLSv1.3")
                context.init(arrayOf(manager), null, null)
                return FamilienTestIdentitaet(alias, fingerprint, context)
            } catch (e: Exception) {
                runCatching { store.deleteEntry(alias) }
                throw e
            }
        }
    }
}
