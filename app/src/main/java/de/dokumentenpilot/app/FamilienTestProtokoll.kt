package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import javax.net.ssl.SSLSocket

/** One exchange per socket. Confirmation callbacks must obtain explicit local consent.
 * Caller must close the socket on cancellation, backgrounding or session deadline.
 */
internal object FamilienTestProtokoll {
    private const val VERSION = 1
    private const val MAX_DATEN = 256
    private const val OK = 1

    fun senden(peer: SSLSocket, sitzung: FamilienTestSitzung, bestaetigen: () -> Boolean): Boolean {
        try {
            check(peer.session.protocol == "TLSv1.3")
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
            if (input.readUnsignedByte() != OK || !bestaetigen() || !sitzung.bestaetigen()) return false
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
                  bestaetigen: () -> Boolean): String? {
        try {
            check(peer.session.protocol == "TLSv1.3")
            FamilienTestEinladungFormat.kodieren(einladung)
            val input = DataInputStream(peer.inputStream)
            val output = DataOutputStream(peer.outputStream)
            val token = Base64.getUrlDecoder().decode(einladung.token)
            try {
                output.writeByte(VERSION)
                output.write(token)
                output.flush()
            } finally { token.fill(0) }
            if (input.readUnsignedByte() != OK || !bestaetigen()) return null
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK) return null
            val groesse = input.readUnsignedShort()
            if (groesse !in 1..MAX_DATEN) return null
            val daten = ByteArray(groesse)
            input.readFully(daten)
            val text = daten.toString(Charsets.UTF_8)
            return text.takeIf { it == "DokumentenPilot: synthetisches Testdokument, keine privaten Daten." }
        } finally { peer.close() }
    }
}
