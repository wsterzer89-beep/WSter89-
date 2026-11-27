package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream

/** Bounded, synchronous archive request over an already authenticated session. */
internal class FamilienArchivAustausch(
    private val katalog: (String) -> List<Eintrag>,
    private val lesen: (String, String, (ByteArray) -> Unit) -> Boolean,
    private val darfAnsehen: (String, String) -> Boolean,
    private val auswaehlen: (String, List<Eintrag>, () -> Boolean) -> Anfrage?,
    private val empfangen: (Eintrag, ByteArray, () -> Boolean) -> Unit,
    private val umbenennen: (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    private val aenderungGemeldet: (Boolean) -> Unit = {},
    private val verschieben: (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    private val loeschen: (String, Eintrag) -> Boolean = { _, _ -> false }
) {
    data class Eintrag(val id: String, val titel: String, val suchbar: Boolean, val assistent: Boolean, val umbenennbar: Boolean = false,
                     val zielKategorien: List<String> = emptyList(), val quellKategorie: String = "", val loeschbar: Boolean = false)
    data class Anfrage(val id: String, val neuerTitel: String? = null, val zielKategorie: String? = null, val loeschen: Boolean = false) {
        init { require(listOf(neuerTitel != null, zielKategorie != null, loeschen).count { it } <= 1) }
    }

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
            output.writeBoolean(it.umbenennbar)
            val ziele = it.zielKategorien.filter(::gueltigeKategorie).distinct().take(MAX_KATEGORIEN)
            output.writeByte(ziele.size)
            ziele.forEach(output::writeUTF)
            output.writeBoolean(it.loeschbar)
        }
        output.flush()
        timeout(8_000)
        val anfrage = try {
            var wahl: Anfrage? = null
            while (aktiv() && wahl == null) {
                when (input.readUnsignedByte()) {
                    0 -> wahl = Anfrage(input.readUTF())
                    2 -> wahl = Anfrage(input.readUTF(), input.readUTF())
                    3 -> wahl = Anfrage(input.readUTF(), zielKategorie = input.readUTF())
                    4 -> wahl = Anfrage(input.readUTF(), loeschen = true)
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
        val id = anfrage.id
        if (id.isEmpty()) return anfrage.neuerTitel == null && anfrage.zielKategorie == null && !anfrage.loeschen
        val eintrag = eintraege.find { it.id == id } ?: return false
        anfrage.neuerTitel?.let { titel ->
            val gespeichert = eintrag.umbenennbar && gueltigerTitel(titel) && aktiv() && umbenennen(peer, id, eintrag.titel, titel)
            output.writeBoolean(gespeichert)
            output.flush()
            return aktiv()
        }
        anfrage.zielKategorie?.let { ziel ->
            val angeboten = eintrag.zielKategorien.filter(::gueltigeKategorie).distinct().take(MAX_KATEGORIEN)
            val gespeichert = ziel in angeboten && aktiv() && verschieben(peer, id, eintrag.quellKategorie, ziel)
            output.writeBoolean(gespeichert)
            output.flush()
            return aktiv()
        }
        if (anfrage.loeschen) {
            val geloescht = eintrag.loeschbar && aktiv() && loeschen(peer, eintrag)
            output.writeBoolean(geloescht)
            output.flush()
            return aktiv()
        }
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
            val suchbar = input.readBoolean()
            val assistent = input.readBoolean()
            val umbenennbar = input.readBoolean()
            val anzahlZiele = input.readUnsignedByte()
            if (anzahlZiele > MAX_KATEGORIEN) return false
            val ziele = List(anzahlZiele) { input.readUTF() }
            if (!ziele.all(::gueltigeKategorie) || ziele.distinct().size != ziele.size) return false
            eintraege.add(Eintrag(id, titel, suchbar, assistent, umbenennbar, ziele, loeschbar = input.readBoolean()))
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
        val eintrag = eintraege.find { it.id == wahl?.id }
        val titel = wahl?.neuerTitel
        if (titel != null) {
            if (eintrag?.umbenennbar != true || !gueltigerTitel(titel)) return false
            output.writeByte(2)
            output.writeUTF(eintrag.id)
            output.writeUTF(titel)
            output.flush()
            val gespeichert = input.readBoolean()
            if (!aktiv()) return false
            aenderungGemeldet(gespeichert)
            return true
        }
        val ziel = wahl?.zielKategorie
        if (ziel != null) {
            if (eintrag == null || ziel !in eintrag.zielKategorien) return false
            output.writeByte(3)
            output.writeUTF(eintrag.id)
            output.writeUTF(ziel)
            output.flush()
            val gespeichert = input.readBoolean()
            if (!aktiv()) return false
            aenderungGemeldet(gespeichert)
            return true
        }
        if (wahl?.loeschen == true) {
            if (eintrag?.loeschbar != true) return false
            output.writeByte(4)
            output.writeUTF(eintrag.id)
            output.flush()
            val geloescht = input.readBoolean()
            if (!aktiv()) return false
            aenderungGemeldet(geloescht)
            return true
        }
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
        private const val MAX_KATEGORIEN = 32
        private val dokumentId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dp")
        private val pdfKopf = "%PDF-".toByteArray(Charsets.US_ASCII)
        fun gueltigerTitel(titel: String): Boolean =
            titel.length in 1..MAX_TITEL && titel.isNotBlank() && titel == titel.trim() &&
                titel.none { it.isISOControl() || it in "|/\\" }

        private fun gueltigeKategorie(name: String) =
            name.length in 1..MAX_TITEL && name.isNotBlank() && name.none { it.isISOControl() }

        fun gueltig(id: String) = dokumentId.matches(id)
        private fun istPdf(bytes: ByteArray) = pdfKopf.indices.all { bytes[it] == pdfKopf[it] }
    }
}
