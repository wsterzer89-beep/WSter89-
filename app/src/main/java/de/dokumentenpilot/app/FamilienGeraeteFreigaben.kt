package de.dokumentenpilot.app

import android.content.Context

/** Local pilot grants; never reads or exposes the private archive. */
internal class FamilienGeraeteFreigaben(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("familien_geraete_freigaben_v1", Context.MODE_PRIVATE)
    private val vertrauen = FamilienGeraeteVertrauen(context)
    private val ids = setOf("demo-1", "demo-2")

    fun speichern(peer: String, id: String, freigegeben: Boolean): Boolean {
        if (id !in ids || vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return false
        return runCatching {
            val editor = prefs.edit()
            if (freigegeben) editor.putBoolean("$peer:$id", true) else editor.remove("$peer:$id")
            editor.commit()
        }.getOrDefault(false)
    }

    fun lesen(peer: String): List<FamilienBerechtigungen.Freigabe> {
        if (vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return emptyList()
        return runCatching {
            ids.filter { prefs.getBoolean("$peer:$it", false) }.map {
                FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.DOKUMENT, it,
                    setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN),
                    archivEigentuemerId = "demo-owner")
            }
        }.getOrDefault(emptyList())
    }
}
