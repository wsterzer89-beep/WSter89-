package de.dokumentenpilot.app

import android.content.Context
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey

internal object FamilienArchivDaten {
    const val SCHLUESSEL = "DokumentenPilotSchluessel"
    data class Eintrag(val dokument: FamilienBerechtigungen.Dokument, val titel: String, val mappen: Map<String, String>)

    fun katalog(
        context: Context, owner: String, index: List<Pair<String, String>>, kategorieVon: (String) -> String
    ): List<Eintrag> {
        val mappen = UnterlagenMappen(context).laden()
        return index.mapNotNull { (id, titel) ->
            val kategorie = kategorieVon(id)
            if (kategorie == MappenImportRegeln.KATEGORIE || !FamilienArchivAustausch.gueltig(id) ||
                !File(context.filesDir, "dokumente/$id").isFile) return@mapNotNull null
            val zuordnung = mappen.filter { id in it.dokumentIds }.associate { it.id to it.name }
            Eintrag(FamilienBerechtigungen.Dokument(id, owner, kategorie, zuordnung.keys), titel, zuordnung)
        }
    }

    fun neuesAngebot(
        peer: String, eintraege: List<Eintrag>, kategorien: List<String>,
        regeln: FamilienArchivFreigaben.Regeln, bearbeiten: Boolean
    ): List<FamilienArchivAustausch.Eintrag> = eintraege.mapNotNull { (doc, titel, mappen) ->
        fun erlaubt(aktion: FamilienBerechtigungen.Aktion) =
            FamilienBerechtigungen.erlaubt(peer, doc, aktion, regeln.freigaben)
        val ansehen = FamilienBerechtigungen.Aktion.ANSEHEN
        if (doc.id in regeln.privat || !erlaubt(ansehen)) return@mapNotNull null
        val sichtbareMappen = mappen.filterKeys { id ->
            regeln.freigaben.any { regel ->
                regel.bereich == FamilienBerechtigungen.Bereich.MAPPE && regel.bereichId == id &&
                    FamilienBerechtigungen.erlaubt(peer, doc, ansehen, listOf(regel))
            }
        }.values.toList()
        // A document or folder grant alone does not authorize publishing its category name.
        val kategorieSichtbar = regeln.freigaben.any { regel ->
            (regel.bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV ||
                regel.bereich == FamilienBerechtigungen.Bereich.KATEGORIE) &&
                FamilienBerechtigungen.erlaubt(peer, doc, ansehen, listOf(regel))
        }
        FamilienArchivAustausch.Eintrag(doc.id, titel,
            erlaubt(FamilienBerechtigungen.Aktion.SUCHEN), erlaubt(FamilienBerechtigungen.Aktion.ASSISTENT),
            bearbeiten && erlaubt(FamilienBerechtigungen.Aktion.UMBENENNEN),
            if (bearbeiten) kategorien.filter { FamilienArchivRegeln.darfVerschieben(peer, doc, it, regeln.freigaben) } else emptyList(),
            doc.kategorie,
            bearbeiten && erlaubt(FamilienBerechtigungen.Aktion.LOESCHEN),
            sichtbareMappen,
            kategorieSichtbar)
    }

    fun vomDatentraeger(context: Context, owner: String): List<Eintrag> {
        val namen = File(context.filesDir, "dokumentnamen.txt")
        if (!namen.isFile) return emptyList()
        val index = namen.useLines { lines ->
            lines.mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) null else line.substring(0, sep) to line.substring(sep + 1)
            }.toList()
        }
        val kategorien = context.getSharedPreferences("dokument_kategorien", Context.MODE_PRIVATE)
        return katalog(context, owner, index) { kategorien.getString(it, "Allgemein") ?: "Allgemein" }
    }

    fun entschluesseln(context: Context, id: String): ByteArray {
        require(FamilienArchivAustausch.gueltig(id))
        val datei = File(context.filesDir, "dokumente/$id")
        val schluessel = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(SCHLUESSEL, null) as? SecretKey ?: error("Archivschlüssel fehlt")
        val verschluesselt = datei.inputStream().use { stream ->
            val laenge = datei.length()
            require(laenge in 1L..(FamilienArchivAustausch.MAX_PDF + 64L))
            ByteArray(laenge.toInt()).also { java.io.DataInputStream(stream).readFully(it) }
        }
        return ArchivKryptografie.entschluesseln(verschluesselt, schluessel)
    }
}
