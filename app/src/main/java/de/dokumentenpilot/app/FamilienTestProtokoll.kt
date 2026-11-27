package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import javax.net.ssl.SSLSocket

/** One exchange per socket. Confirmation callbacks must obtain explicit local consent.
 * Caller must close the socket on cancellation, backgrounding or session deadline.
 */
internal object FamilienTestProtokoll {
    private const val VERSION = 14
    private const val MAX_DATEN = 256
    private const val OK = 1

    fun senden(peer: SSLSocket, sitzung: FamilienTestSitzung, aktiv: () -> Boolean = { true }, vertrauen: FamilienGeraeteVertrauen? = null, lokaleIdentitaet: FamilienGeraeteIdentitaet = FamilienGeraeteIdentitaet.laden(), freigegebeneIds: ((String) -> Set<String>)? = null, pdfEmpfangen: ((String, ByteArray) -> Unit)? = null, weitereAufnahme: Boolean = false, archiv: FamilienArchivAustausch? = null, bestaetigen: (String, String) -> Boolean): Boolean {
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
            val fingerprint = identitaet(input, output, true, aktiv, lokaleIdentitaet, peer) ?: return false
            if ((if (weitereAufnahme) vertrauen?.aufnahmeStatus(fingerprint) else vertrauen?.status(fingerprint)) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
            if (!bestaetigen(lokaleIdentitaet.fingerprint, fingerprint) || input.readUnsignedByte() != OK || !sitzung.bestaetigen()) return false
            if (!aktiv() || (if (weitereAufnahme) vertrauen?.aufnahmeStatus(fingerprint) else vertrauen?.status(fingerprint)) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK) return false
            if (!aktiv()) return false
            val daten = sitzung.testdaten() ?: return false
            if ((if (weitereAufnahme) vertrauen?.weiteresGeraetBestaetigen(fingerprint) else vertrauen?.bestaetigen(fingerprint)) == false || !aktiv()) return false
            check(daten.size in 1..MAX_DATEN)
            val ids = freigegebeneIds?.invoke(fingerprint).orEmpty()
            if (!ids.all { it == "demo-1" || it == "demo-2" } || !aktiv() ||
                vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
            output.writeByte(OK)
            output.writeShort(daten.size)
            output.write(daten)
            if (!pdfSenden(output, ids, fingerprint, aktiv, vertrauen, freigegebeneIds)) return false
            output.flush()
            if (input.readUnsignedByte() != OK || !aktiv()) return false
            pdfLesen(input, fingerprint, aktiv, vertrauen, pdfEmpfangen) ?: return false
            if (!aktiv() || vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
            output.writeByte(OK)
            output.flush()
            if (!archivAustauschen(input, output, fingerprint, true, archiv, vertrauen, aktiv) { peer.soTimeout = it }) return false
            return aktiv() &&
                vertrauen?.status(fingerprint) != FamilienGeraeteVertrauen.Status.ABGELEHNT
        } finally {
            sitzung.beenden()
            peer.close()
        }
    }

    fun empfangen(peer: SSLSocket, einladung: FamilienTestEinladungFormat.Einladung,
                  aktiv: () -> Boolean = { true }, vertrauen: FamilienGeraeteVertrauen? = null, lokaleIdentitaet: FamilienGeraeteIdentitaet = FamilienGeraeteIdentitaet.laden(), pdfEmpfangen: ((String, ByteArray) -> Unit)? = null, freigegebeneIds: ((String) -> Set<String>)? = null, weitereAufnahme: Boolean = false, archiv: FamilienArchivAustausch? = null, bestaetigen: (String, String) -> Boolean): String? {
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
            val fingerprint = identitaet(input, output, false, aktiv, lokaleIdentitaet, peer) ?: return null
            if ((if (weitereAufnahme) vertrauen?.aufnahmeStatus(fingerprint) else vertrauen?.status(fingerprint)) == FamilienGeraeteVertrauen.Status.ABGELEHNT || !bestaetigen(lokaleIdentitaet.fingerprint, fingerprint)) return null
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK) return null
            if (!aktiv() || (if (weitereAufnahme) vertrauen?.weiteresGeraetBestaetigen(fingerprint)
                else vertrauen?.bestaetigen(fingerprint)) == false) return null
            output.writeByte(OK)
            output.flush()
            if (input.readUnsignedByte() != OK || !aktiv()) return null
            val groesse = input.readUnsignedShort()
            if (groesse !in 1..MAX_DATEN) return null
            val daten = ByteArray(groesse)
            input.readFully(daten)
            val text = daten.toString(Charsets.UTF_8)
            if (!aktiv() || vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT ||
                text != "DokumentenPilot: synthetisches Testdokument, keine privaten Daten.") return null
            val liste = pdfLesen(input, fingerprint, aktiv, vertrauen, pdfEmpfangen) ?: return null
            if (!aktiv() || vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return null
            output.writeByte(OK)
            output.flush()
            val eigeneIds = freigegebeneIds?.invoke(fingerprint).orEmpty()
            if (!pdfSenden(output, eigeneIds, fingerprint, aktiv, vertrauen, freigegebeneIds)) return null
            output.flush()
            if (input.readUnsignedByte() != OK) return null
            if (!archivAustauschen(input, output, fingerprint, false, archiv, vertrauen, aktiv) { peer.soTimeout = it }) return null
            return (if (liste.isEmpty()) text else text + "\n\nFreigegebene Testdokumente:\n" + liste.joinToString("\n")).takeIf { aktiv() && vertrauen?.status(fingerprint) != FamilienGeraeteVertrauen.Status.ABGELEHNT }
        } finally { peer.close() }
    }

    private fun archivAustauschen(
        input: DataInputStream, output: DataOutputStream, peer: String, server: Boolean,
        archiv: FamilienArchivAustausch?, vertrauen: FamilienGeraeteVertrauen?, aktiv: () -> Boolean, timeout: (Int) -> Unit
    ): Boolean {
        fun erlaubt() = aktiv() && vertrauen?.status(peer) == FamilienGeraeteVertrauen.Status.BESTAETIGT
        val lokal = archiv != null && erlaubt()
        output.writeBoolean(lokal)
        output.flush()
        val remote = input.readBoolean()
        if (lokal != remote) return false
        if (!lokal) return aktiv()
        return if (server) {
            archiv.senden(peer, input, output, ::erlaubt, timeout) && archiv.empfangen(peer, input, output, ::erlaubt, timeout)
        } else {
            archiv.empfangen(peer, input, output, ::erlaubt, timeout) && archiv.senden(peer, input, output, ::erlaubt, timeout)
        }
    }

    private fun pdfSenden(output: DataOutputStream, ids: Set<String>, fingerprint: String, aktiv: () -> Boolean, vertrauen: FamilienGeraeteVertrauen?, freigegebeneIds: ((String) -> Set<String>)?): Boolean {
        if (!ids.all { testInhalt(it) != null } || !aktiv() ||
            vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) {
            return false
        }
        output.writeByte(ids.size)
            for (id in ids.sorted()) {
                if (!aktiv() || vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return false
                val pdf = FamilienTestPdf.erstellen(id) ?: return false
                try {
                    if (!aktiv() || vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT ||
                        id !in freigegebeneIds?.invoke(fingerprint).orEmpty()) return false
                    output.writeUTF(id)
                    output.writeUTF(testInhalt(id) ?: return false)
                    output.writeInt(pdf.size)
                    output.write(pdf)
                } finally { pdf.fill(0) }
            }
        return true
    }

    private fun pdfLesen(input: DataInputStream, fingerprint: String, aktiv: () -> Boolean, vertrauen: FamilienGeraeteVertrauen?, pdfEmpfangen: ((String, ByteArray) -> Unit)?): List<String>? {
            val anzahl = input.readUnsignedByte()
            if (anzahl !in 0..2) return null
            val gesehen = mutableSetOf<String>()
            val liste = mutableListOf<String>()
            repeat(anzahl) {
                if (!aktiv()) return null
                val id = input.readUTF()
                val inhalt = input.readUTF()
                if (!gesehen.add(id) || testInhalt(id) != inhalt) return null
                val pdfGroesse = input.readInt()
                if (pdfGroesse !in 5..FamilienTestPdf.MAX_BYTES) return null
                val pdf = ByteArray(pdfGroesse)
                try {
                    input.readFully(pdf)
                    if (!pdf.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) || !aktiv()) return null
                    if (vertrauen?.status(fingerprint) == FamilienGeraeteVertrauen.Status.ABGELEHNT) return null
                    // Borrowed buffer: consumer must not retain it; cleared in finally.
                    pdfEmpfangen?.invoke(id, pdf)
                    liste.add(inhalt + "\nPDF empfangen: " + pdfGroesse + " Bytes (Test, nicht gespeichert)")
                } finally { pdf.fill(0) }
            }
        return liste
    }

    private fun testInhalt(id: String): String? = when (id) {
        "demo-1" -> "Beispieldokument 1 – synthetische Familienunterlagen\nTestinhalt: Familienakte, Musterfall 001."
        "demo-2" -> "Beispieldokument 2 – synthetische Vertragsunterlagen\nTestinhalt: Mustervertrag, Vorgang 002."
        else -> null
    }

    /** Proof of possession only; existing explicit consent remains mandatory.
     * Returns the verified peer fingerprint for the trust check.
     */
    private fun identitaet(input: DataInputStream, output: DataOutputStream,
                           server: Boolean, aktiv: () -> Boolean, local: FamilienGeraeteIdentitaet, peer: SSLSocket): String? {
        if (!aktiv()) return null
        val cert = if (server) peer.session.localCertificates?.firstOrNull()
                   else peer.session.peerCertificates.firstOrNull()
        val tlsPin = java.security.MessageDigest.getInstance("SHA-256")
            .digest(cert?.encoded ?: return null)
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
                out.writeUTF("FamilienTestProtokoll/v14")
                out.write(tlsPin)
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
