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

    fun umfangSpeichern(peer: String, bereich: FamilienBerechtigungen.Bereich,
        id: String, dokumentIds: Set<String>, automatisch: Boolean, aktiv: Boolean): Boolean {
        if (vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT ||
            bereich == FamilienBerechtigungen.Bereich.DOKUMENT ||
            !ids.containsAll(dokumentIds) || id.length > 80 ||
            (bereich != FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV && id.isBlank()) ||
            (bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV && id.isNotEmpty())) return false
        return runCatching {
            val key = "$peer:umfang:${bereich.name}:$id"
            val editor = prefs.edit()
            if (aktiv) editor.putStringSet(key, dokumentIds + if (automatisch) setOf("auto") else emptySet())
            else editor.remove(key)
            editor.commit()
        }.getOrDefault(false)
    }

    fun ausnahmenSpeichern(peer: String, dokumente: Set<String>): Boolean {
        if (!ids.containsAll(dokumente) || vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return false
        return runCatching { prefs.edit().putStringSet("$peer:ausnahmen", dokumente.toSet()).commit() }.getOrDefault(false)
    }

    fun ausnahmen(peer: String): Set<String> {
        if (vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return ids
        return runCatching {
            val gespeichert = prefs.getStringSet("$peer:ausnahmen", emptySet()) ?: return ids
            if (ids.containsAll(gespeichert)) gespeichert.toSet() else ids
        }.getOrDefault(ids)
    }

    fun lesen(peer: String): List<FamilienBerechtigungen.Freigabe> {
        if (vertrauen.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT) return emptyList()
        return runCatching {
            ids.filter { prefs.getBoolean("$peer:$it", false) }.map {
                FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.DOKUMENT, it,
                    setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN),
                    archivEigentuemerId = "demo-owner")
            } + prefs.all.mapNotNull { (key, value) ->
                val prefix = "$peer:umfang:"
                if (!key.startsWith(prefix)) return@mapNotNull null
                val parts = key.removePrefix(prefix).split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val bereich = FamilienBerechtigungen.Bereich.valueOf(parts[0])
                if (bereich == FamilienBerechtigungen.Bereich.DOKUMENT) return@mapNotNull null
                val werte = (value as? Set<*>) ?: return@mapNotNull null
                if (werte.any { it !in ids && it != "auto" }) return@mapNotNull null
                FamilienBerechtigungen.Freigabe(peer, bereich, parts[1],
                    setOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN),
                    dokumentIdsBeiFreigabe = ids.filter { it in werte }.toSet(),
                    neueDokumenteAutomatisch = "auto" in werte, archivEigentuemerId = "demo-owner")
            }
        }.getOrDefault(emptyList())
    }
}
