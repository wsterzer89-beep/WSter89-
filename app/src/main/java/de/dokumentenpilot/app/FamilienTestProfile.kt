package de.dokumentenpilot.app

import android.content.Context

/** Only synthetic local profiles. Never authorizes archive access or identifies a peer. */
internal class FamilienTestProfile(context: Context) {
    private val prefs = context.getSharedPreferences("familien_testprofile", Context.MODE_PRIVATE)
    private val ids = setOf("demo-1", "demo-2")
    private val aktionen = setOf("ANSEHEN", "SUCHEN", "EXPORTIEREN", "BEARBEITEN", "LOESCHEN")

    fun vorhanden(): Boolean = runCatching { prefs.getBoolean("demo_member", false) }.getOrDefault(false)

    fun speichern(): Boolean = prefs.edit().clear().putBoolean("demo_member", true).commit()

    fun entfernen(): Boolean = prefs.edit().clear().commit()

    fun freigaben(): List<FamilienBerechtigungen.Freigabe> {
        if (!vorhanden()) return emptyList()
        return runCatching {
            ids.mapNotNull { id ->
                val namen = prefs.getStringSet(id, emptySet()).orEmpty()
                require(namen.all { it in aktionen })
                if (namen.isEmpty()) null else FamilienBerechtigungen.Freigabe(
                    "demo-member", FamilienBerechtigungen.Bereich.DOKUMENT, id,
                    namen.map { FamilienBerechtigungen.Aktion.valueOf(it) }.toSet(),
                    archivEigentuemerId = "demo-owner")
            }
        }.getOrDefault(emptyList())
    }

    fun rechteSpeichern(id: String, rechte: Set<FamilienBerechtigungen.Aktion>): Boolean {
        if (!vorhanden() || id !in ids || rechte.any { it.name !in aktionen }) return false
        val editor = prefs.edit()
        if (rechte.isEmpty()) editor.remove(id) else editor.putStringSet(id, rechte.map { it.name }.toSet())
        return editor.commit()
    }
}
