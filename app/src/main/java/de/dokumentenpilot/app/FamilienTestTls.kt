package de.dokumentenpilot.app

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.cert.CertificateException
import javax.net.ssl.X509TrustManager

/** Trust only the exact certificate obtained through the out-of-band invitation.
 * This is not a general-purpose trust manager and must not be used for other connections.
 */
internal class FamilienTestTls(private val fingerprint: String) : X509TrustManager {
    init { require(Regex("[0-9a-f]{64}").matches(fingerprint)) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty() || authType.isNullOrBlank()) throw CertificateException("Missing peer certificate")
        val cert = chain[0]
        cert.checkValidity()
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val expected = ByteArray(32) { i -> fingerprint.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        if (!MessageDigest.isEqual(actual, expected)) throw CertificateException("Peer certificate mismatch")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificate trust not configured")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
