package de.dokumentenpilot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Archive policy for each verified peer and local owner. */
internal class FamilienArchivFreigaben(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("familien_archiv_regeln_v1", Context.MODE_PRIVATE)
    private val trust = FamilienGeraeteVertrauen(context)
    data class Regeln(val freigaben: List<FamilienBerechtigungen.Freigabe>, val privat: Set<String>)

    fun speichern(owner: String, peer: String, regeln: Regeln,
                  katalog: List<FamilienBerechtigungen.Dokument>): Boolean = synchronized(lock) {
        val bisher = lesen(owner, peer) ?: return@synchronized false
        if (trust.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT ||
            owner == peer || owner.isBlank() || regeln.freigaben.size > 1000 ||
            regeln.freigaben.distinctBy { it.bereich to it.bereichId }.size != regeln.freigaben.size ||
            !regeln.freigaben.all { it in bisher.freigaben || FamilienArchivRegeln.validieren(owner, peer, it, katalog) } ||
            !(regeln.privat - bisher.privat).all { id -> katalog.any { it.id == id && it.eigentuemerId == owner } }) return@synchronized false
        runCatching {
            val array = JSONArray()
            regeln.freigaben.forEach { r ->
                array.put(JSONObject().put("scope", r.bereich.name).put("id", r.bereichId)
                    .put("actions", JSONArray(r.erlaubteAktionen.map { it.name }))
                    .put("snapshot", JSONArray(r.dokumentIdsBeiFreigabe.toList()))
                    .put("auto", r.neueDokumenteAutomatisch))
            }
            prefs.edit().putString(peer, JSONObject().put("owner", owner).put("rules", array)
                .put("private", JSONArray(regeln.privat.toList())).toString()).commit()
        }.getOrDefault(false)
    }

    fun lesen(owner: String, peer: String): Regeln? = synchronized(lock) {
        if (trust.status(peer) != FamilienGeraeteVertrauen.Status.BESTAETIGT || owner == peer || owner.isBlank()) return@synchronized null
        runCatching {
            val json = JSONObject(prefs.getString(peer, null) ?: return@synchronized Regeln(emptyList(), emptySet()))
            if (json.getString("owner") != owner) return@synchronized null
            val array = json.getJSONArray("rules")
            require(array.length() <= 1000)
            val rules = (0 until array.length()).map { i ->
                val r = array.getJSONObject(i)
                val actions = strings(r.getJSONArray("actions")).map { FamilienBerechtigungen.Aktion.valueOf(it) }.toSet()
                require(actions.isNotEmpty() && actions.all { it == FamilienBerechtigungen.Aktion.ANSEHEN || it == FamilienBerechtigungen.Aktion.SUCHEN || it == FamilienBerechtigungen.Aktion.ASSISTENT })
                FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.valueOf(r.getString("scope")),
                    r.getString("id"), actions, strings(r.getJSONArray("snapshot")), r.getBoolean("auto"),
                    archivEigentuemerId = owner)
            }
            Regeln(rules, strings(json.getJSONArray("private")))
        }.getOrNull()
    }

    fun entfernen(peer: String): Boolean = synchronized(lock) {
        runCatching { prefs.edit().remove(peer).commit() }.getOrDefault(false)
    }

    private fun strings(a: JSONArray): Set<String> = (0 until a.length()).map { a.getString(it) }.toSet()
    companion object { private val lock = Any() }
}
