package de.dokumentenpilot.app

import java.io.DataInputStream
import java.io.DataOutputStream

/** Bounded, synchronous archive request over an already authenticated session. */
internal class FamilienArchivAustausch(
    private val katalog: (String) -> List<Eintrag>,
    private val lesen: (String, String, (ByteArray) -> Unit) -> Boolean,
    private val darfAnsehen: (String, String) -> Boolean,
    private val auswaehlen: (String, List<Eintrag>, List<String>, () -> Boolean) -> Anfrage?,
    private val empfangen: (Eintrag, ByteArray, () -> Boolean) -> Unit,
    private val umbenennen: (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    private val aenderungGemeldet: (Boolean) -> Unit = {},
    private val verschieben: (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    private val loeschen: (String, Eintrag) -> Boolean = { _, _ -> false },
    private val einreichZiele: (String) -> List<String> = { emptyList() },
    private val darfEinreichen: (String, String) -> Boolean = { _, _ -> false },
    private val lokalePdf: (String) -> LokalePdf? = { null },
    private val pdfEinreichen: (String, String, String, ByteArray) -> Boolean = { _, _, _, _ -> false }
) {
    data class Eintrag(val id: String, val titel: String, val suchbar: Boolean, val assistent: Boolean, val umbenennbar: Boolean = false,
                     val zielKategorien: List<String> = emptyList(), val quellKategorie: String = "", val loeschbar: Boolean = false, val mappen: List<String> = emptyList())
    data class Anfrage(val id: String, val neuerTitel: String? = null, val zielKategorie: String? = null, val loeschen: Boolean = false, val einreichKategorie: String? = null) {
        init { require(listOf(neuerTitel != null, zielKategorie != null, loeschen, einreichKategorie != null).count { it } <= 1) }
    }

    private var weitereAuswahl = false
    fun weiterAnzeigen(): Boolean = weitereAuswahl

    data class LokalePdf(val titel: String, val bytes: ByteArray)

    fun senden(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, timeout: (Int) -> Unit): Boolean =
        senden(peer, input, output, aktiv, metadaten = false, timeout = timeout)

    fun senden(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, metadaten: Boolean, timeout: (Int) -> Unit): Boolean {
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
            if (metadaten) {
                output.writeUTF(it.quellKategorie.take(MAX_TITEL))
                val mappen = it.mappen.filter(::gueltigerTitel).distinct().take(MAX_KATEGORIEN)
                output.writeByte(mappen.size)
                mappen.forEach(output::writeUTF)
            }
        }
        val importZiele = einreichZiele(peer).filter(::gueltigeKategorie).distinct().take(MAX_KATEGORIEN)
        if (!aktiv()) return false
        output.writeByte(importZiele.size)
        importZiele.forEach(output::writeUTF)
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
                    5 -> wahl = Anfrage(input.readUTF(), einreichKategorie = input.readUTF())
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
        anfrage.einreichKategorie?.let { ziel ->
            val titel = anfrage.id
            val erlaubt = ziel in importZiele && gueltigerTitel(titel) && darfEinreichen(peer, ziel) && aktiv()
            output.writeBoolean(erlaubt)
            output.flush()
            if (!erlaubt) return aktiv()
            val groesse = input.readInt()
            if (groesse !in 5..MAX_PDF || !aktiv()) return false
            val bytes = ByteArray(groesse)
            val gespeichert = try {
                input.readFully(bytes)
                istPdf(bytes) && aktiv() && darfEinreichen(peer, ziel) && pdfEinreichen(peer, ziel, titel, bytes)
            } finally {
                bytes.fill(0)
            }
            output.writeBoolean(gespeichert)
            output.flush()
            return aktiv()
        }
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

    fun empfangen(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, timeout: (Int) -> Unit): Boolean =
        empfangen(peer, input, output, aktiv, metadaten = false, timeout = timeout)

    fun empfangen(peer: String, input: DataInputStream, output: DataOutputStream, aktiv: () -> Boolean, metadaten: Boolean, timeout: (Int) -> Unit): Boolean {
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
            val loeschbar = input.readBoolean()
            val kategorie = if (metadaten) input.readUTF() else ""
            if (kategorie.isNotEmpty() && !gueltigerTitel(kategorie)) return false
            val anzahlMappen = if (metadaten) input.readUnsignedByte() else 0
            if (anzahlMappen > MAX_KATEGORIEN) return false
            val mappen = List(anzahlMappen) { input.readUTF() }
            if (!mappen.all(::gueltigerTitel) || mappen.distinct().size != mappen.size) return false
            eintraege.add(Eintrag(id, titel, suchbar, assistent, umbenennbar, ziele, kategorie, loeschbar, mappen))
        }
        val importAnzahl = input.readUnsignedByte()
        if (importAnzahl > MAX_KATEGORIEN || !aktiv()) return false
        val importZiele = List(importAnzahl) { input.readUTF() }
        if (!importZiele.all(::gueltigeKategorie) || importZiele.distinct().size != importZiele.size) return false
        timeout(3_000)
        val wahl = try {
            if (eintraege.isEmpty() && importZiele.isEmpty()) null
            else auswaehlen(peer, eintraege, importZiele) {
                if (!aktiv()) return@auswaehlen false
                output.writeByte(1)
                output.flush()
                input.readBoolean() && aktiv()
            }
        } finally {
            timeout(150_000)
        }
        if (!aktiv()) return false
        weitereAuswahl = wahl != null
        wahl?.einreichKategorie?.let { ziel ->
            if (ziel !in importZiele || !gueltig(wahl.id)) return false
            val pdf = lokalePdf(wahl.id)
            if (pdf == null) {
                output.writeByte(0)
                output.writeUTF("")
                output.flush()
                aenderungGemeldet(false)
                return aktiv()
            }
            try {
                if (!gueltigerTitel(pdf.titel) || pdf.bytes.size !in 5..MAX_PDF ||
                    !istPdf(pdf.bytes) || !aktiv()) return false
                output.writeByte(5)
                output.writeUTF(pdf.titel)
                output.writeUTF(ziel)
                output.flush()
                if (!input.readBoolean()) {
                    aenderungGemeldet(false)
                    return aktiv()
                }
                if (!aktiv()) return false
                output.writeInt(pdf.bytes.size)
                output.write(pdf.bytes)
                output.flush()
                val gespeichert = input.readBoolean()
                if (!aktiv()) return false
                aenderungGemeldet(gespeichert)
                return true
            } finally {
                pdf.bytes.fill(0)
            }
        }
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

        fun einreichId(peer: String, kategorie: String, bytes: ByteArray): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update("$peer\u0000$kategorie\u0000".toByteArray(Charsets.UTF_8))
            return "${java.util.UUID.nameUUIDFromBytes(digest.digest(bytes))}.dp"
        }

        fun gueltig(id: String) = dokumentId.matches(id)
        private fun istPdf(bytes: ByteArray) = pdfKopf.indices.all { bytes[it] == pdfKopf[it] }
    }
}
