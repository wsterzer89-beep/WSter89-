package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import javax.net.ssl.SSLSocket

/** One exchange per socket. Confirmation callbacks must obtain explicit local consent.
 * Caller must close the socket on cancellation, backgrounding or session deadline.
 */
internal object FamilienTestProtokoll {
    private const val VERSION = 3
    private const val MAX_DATEN = 256
    private const val OK = 1

    fun senden(peer: SSLSocket, sitzung: FamilienTestSitzung, aktiv: () -> Boolean = { true }, vertrauen: FamilienGeraeteVertrauen? = null, bestaetigen: () -> Boolean): Boolean {
        try {
            check(peer.session.protocol == "TLSv1.3")
            peer.soTimeout = 150000
            val input = DataInputStream(peer.inputStream)
            val output = DataOutputStream(peer.outputStream)
            if (input.readUnsignedByte() != VERSION) return false
            val token = ByteArray(32)
            val angenommen = try {
                input.readFully(token)
                sitzung.annehmen(token)
            } finally { token.fill(0) }
            if (!angenommen) return false
            output.writeByte(OK)
            output.flush()
            val fingerprint = identitaet(input, output, true, aktiv) ?: return false
            if (vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
            if (input.readUnsignedByte() != OK || !bestaetigen() || !sitzung.bestaetigen()) return false
            if (!aktiv() || vertrauen?.bestaetigen(fingerprint) == false) return false
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK) return false
            if (!aktiv()) return false
            val daten = sitzung.testdaten() ?: return false
            check(daten.size in 1..MAX_DATEN)
            output.writeByte(OK)
            output.writeShort(daten.size)
            output.write(daten)
            output.flush()
            return true
        } finally {
            sitzung.beenden()
            peer.close()
        }
    }

    fun empfangen(peer: SSLSocket, einladung: FamilienTestEinladungFormat.Einladung,
                  aktiv: () -> Boolean = { true }, vertrauen: FamilienGeraeteVertrauen? = null, bestaetigen: () -> Boolean): String? {
        try {
            check(peer.session.protocol == "TLSv1.3")
            FamilienTestEinladungFormat.kodieren(einladung)
            peer.soTimeout = 150000
            val input = DataInputStream(peer.inputStream)
            val output = DataOutputStream(peer.outputStream)
            val token = Base64.getUrlDecoder().decode(einladung.token)
            try {
                output.writeByte(VERSION)
                output.write(token)
                output.flush()
            } finally { token.fill(0) }
            if (input.readUnsignedByte() != OK) return null
            val fingerprint = identitaet(input, output, false, aktiv) ?: return null
            if (vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT || !bestaetigen()) return null
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK) return null
            if (!aktiv() || vertrauen?.bestaetigen(fingerprint) == false) return null
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK || !aktiv()) return null
            val groesse = input.readUnsignedShort()
            if (groesse !in 1..MAX_DATEN) return null
            val daten = ByteArray(groesse)
            input.readFully(daten)
            val text = daten.toString(Charsets.UTF_8)
            return text.takeIf { aktiv() && it == "DokumentenPilot: synthetisches Testdokument, keine privaten Daten." }
        } finally { peer.close() }
    }

    /** Proof of possession only; existing explicit consent remains mandatory.
     * Returns the verified peer fingerprint for the trust check.
     */
    private fun identitaet(input: DataInputStream, output: DataOutputStream,
                           server: Boolean, aktiv: () -> Boolean): String? {
        if (!aktiv()) return null
        val local = FamilienGeraeteIdentitaet.laden()
        val key = local.publicKey
        val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        fun writeField(bytes: ByteArray) {
            output.writeShort(bytes.size)
            output.write(bytes)
        }
        fun readField(max: Int): ByteArray {
            val size = input.readUnsignedShort()
            require(size in 1..max)
            return ByteArray(size).also { input.readFully(it) }
        }
        writeField(key)
        output.write(nonce)
        output.flush()
        val remoteKey = readField(256)
        val remoteNonce = ByteArray(32).also { input.readFully(it) }
        if (key.contentEquals(remoteKey) || !aktiv()) return null
        fun transcript(serverRole: Boolean): ByteArray {
            val buffer = java.io.ByteArrayOutputStream()
            DataOutputStream(buffer).use { out ->
                out.writeUTF("FamilienTestProtokoll/v3")
                out.writeBoolean(serverRole)
                for (field in if (server) listOf(key, remoteKey, nonce, remoteNonce)
                              else listOf(remoteKey, key, remoteNonce, nonce)) {
                    out.writeShort(field.size)
                    out.write(field)
                }
            }
            return buffer.toByteArray()
        }
        writeField(local.signiere(transcript(server)))
        output.flush()
        val signature = readField(80)
        if (!aktiv() || !FamilienGeraeteIdentitaet.pruefen(remoteKey, transcript(!server), signature)) return null
        return java.security.MessageDigest.getInstance("SHA-256").digest(remoteKey)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
