package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream

/** Bounded, synchronous archive request over an already authenticated session. */
internal class FamilienArchivAustausch(
    private val katalog: (String) -> List<Eintrag>,
    private val lesen: (String, String, (ByteArray) -> Unit) -> Boolean,
    private val darfAnsehen: (String, String) -> Boolean,
    private val auswaehlen: (String, List<Eintrag>, () -> Boolean) -> String?,
    private val empfangen: (Eintrag, ByteArray, () -> Boolean) -> Unit
) {
    data class Eintrag(val id: String, val titel: String, val suchbar: Boolean, val assistent: Boolean)

    fun senden(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, timeout: (Int) -> Unit): Boolean {
        if (!aktiv()) return false
        val eintraege = katalog(peer).filter { gueltig(it.id) }.distinctBy { it.id }.take(MAX_EINTRAEGE)
        if (!aktiv()) return false
        output.writeInt(eintraege.size)
        eintraege.forEach {
            if (!aktiv()) return false
            output.writeUTF(it.id)
            output.writeUTF(it.titel.take(MAX_TITEL))
            output.writeBoolean(it.suchbar)
            output.writeBoolean(it.assistent)
        }
        output.flush()
        timeout(8_000)
        val id = try {
            var wahl: String? = null
            while (aktiv() && wahl == null) {
                when (input.readUnsignedByte()) {
                    0 -> wahl = input.readUTF()
                    1 -> {
                        output.writeBoolean(aktiv())
                        output.flush()
                    }
                    else -> return false
                }
            }
            wahl ?: return false
        } finally {
            timeout(150_000)
        }
        if (!aktiv()) return false
        if (id.isEmpty()) return true
        if (eintraege.none { it.id == id }) return false
        var begonnen = false
        val gesendet = lesen(peer, id) { bytes ->
            if (!aktiv() || bytes.size !in 5..MAX_PDF || !istPdf(bytes)) return@lesen
            begonnen = true
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
        if (!aktiv()) return false
        if (!begonnen) {
            output.writeInt(0)
            output.flush()
            return true
        }
        if (!gesendet) return false
        timeout(8_000)
        try {
            while (aktiv()) {
                when (input.readUnsignedByte()) {
                    0 -> return true
                    1 -> {
                        val erlaubt = darfAnsehen(peer, id) && aktiv()
                        output.writeBoolean(erlaubt)
                        output.flush()
                        if (!erlaubt) return false
                    }
                    else -> return false
                }
            }
            return false
        } finally {
            timeout(150_000)
        }
    }

    fun empfangen(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, timeout: (Int) -> Unit): Boolean {
        val anzahl = input.readInt()
        if (anzahl !in 0..MAX_EINTRAEGE || !aktiv()) return false
        val ids = mutableSetOf<String>()
        val eintraege = ArrayList<Eintrag>(anzahl)
        repeat(anzahl) {
            if (!aktiv()) return false
            val id = input.readUTF()
            val titel = input.readUTF()
            if (!gueltig(id) || !ids.add(id) || titel.length > MAX_TITEL) return false
            eintraege.add(Eintrag(id, titel, input.readBoolean(), input.readBoolean()))
        }
        timeout(3_000)
        val wahl = try {
            if (eintraege.isEmpty()) null
            else auswaehlen(peer, eintraege) {
                if (!aktiv()) return@auswaehlen false
                output.writeByte(1)
                output.flush()
                input.readBoolean() && aktiv()
            }
        } finally {
            timeout(150_000)
        }
        if (!aktiv()) return false
        val eintrag = eintraege.find { it.id == wahl }
        output.writeByte(0)
        output.writeUTF(eintrag?.id.orEmpty())
        output.flush()
        if (eintrag == null) return true
        val groesse = input.readInt()
        if (groesse == 0) return aktiv()
        if (groesse !in 5..MAX_PDF || !aktiv()) return false
        val bytes = ByteArray(groesse)
        try {
            input.readFully(bytes)
            if (!aktiv() || !istPdf(bytes)) return false
            fun verlaengern(): Boolean {
                if (!aktiv()) return false
                output.writeByte(1)
                output.flush()
                return input.readBoolean() && aktiv()
            }
            timeout(3_000)
            try {
                if (!verlaengern()) return false
                empfangen(eintrag, bytes, ::verlaengern)
                if (!aktiv()) return false
                output.writeByte(0)
                output.flush()
                return true
            } finally {
                timeout(150_000)
            }
        } finally {
            bytes.fill(0)
        }
    }

    companion object {
        const val MAX_PDF = 20 * 1024 * 1024
        private const val MAX_EINTRAEGE = 500
        private const val MAX_TITEL = 200
        private val dokumentId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dp")
        private val pdfKopf = "%PDF-".toByteArray(Charsets.US_ASCII)
        fun gueltig(id: String) = dokumentId.matches(id)
        private fun istPdf(bytes: ByteArray) = pdfKopf.indices.all { bytes[it] == pdfKopf[it] }
    }
}
