package de.dokumentenpilot.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UnterlagenMappe(
    val id: String,
    val name: String,
    val dokumentIds: MutableList<String>,
    val angeforderteUnterlagen: MutableList<String> = mutableListOf(),
    val anforderungen: MutableList<UnterlagenAnforderung> = mutableListOf(),
    val manuelleNachweise: MutableMap<String, MutableList<String>> = mutableMapOf()
)

class UnterlagenMappen(private val context: Context) {

    private val datei = File(context.filesDir, "unterlagen_mappen.json")

    fun laden(): MutableList<UnterlagenMappe> {
        // Auch ein nach einem Absturz verbliebenes .bak wiederherstellen.
        if (!datei.exists() && !File(datei.path + ".bak").exists()) {
            return mutableListOf()
        }

        val ergebnis = mutableListOf<UnterlagenMappe>()
        val array = JSONArray(
            AtomicFile(datei).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        )

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            val ids = mutableListOf<String>()
            val dokumente = obj.getJSONArray("dokumentIds")
            for (j in 0 until dokumente.length()) {
                ids.add(dokumente.getString(j))
            }

            val unterlagen = mutableListOf<String>()
            val liste = obj.optJSONArray("angeforderteUnterlagen")
            if (liste != null) {
                for (k in 0 until liste.length()) {
                    unterlagen.add(liste.getString(k))
                }
            }

            val anforderungen = mutableListOf<UnterlagenAnforderung>()
            val gespeicherte = obj.optJSONArray("anforderungen")

            if (gespeicherte != null) {
                for (k in 0 until gespeicherte.length()) {
                    val eintrag = gespeicherte.optJSONObject(k)
                        ?: continue

                    val art = eintrag.optString("dokumentenart")
                    if (art.isBlank()) continue

                    anforderungen.add(
                        UnterlagenAnforderung(
                            dokumentenart = art,
                            anzahl = if (eintrag.isNull("anzahl")) {
                                null
                            } else {
                                eintrag.optInt("anzahl").takeIf { it > 0 }
                            },
                            neuesteZuerst =
                                eintrag.optBoolean("neuesteZuerst", false),
                            jahr = if (eintrag.isNull("jahr")) {
                                null
                            } else {
                                eintrag.optInt("jahr").takeIf {
                                    it in 2000..2099
                                }
                            },
                            monatVon = eintrag.optInt("monatVon").takeIf { it in 1..12 },
                            monatBis = eintrag.optInt("monatBis").takeIf { it in 1..12 },
                            namensFilter = if (eintrag.isNull("namensFilter")) null else
                                eintrag.optString("namensFilter").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val manuell = mutableMapOf<String, MutableList<String>>()
            obj.optJSONObject("manuelleNachweise")?.let { daten ->
                daten.keys().forEach { art ->
                    val liste = daten.optJSONArray(art) ?: return@forEach
                    manuell[art] = MutableList(liste.length()) { liste.getString(it) }
                }
            }
            ergebnis.add(
                UnterlagenMappe(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    dokumentIds = ids,
                    angeforderteUnterlagen = unterlagen,
                    anforderungen = anforderungen,
                    manuelleNachweise = manuell
                )
            )
        }

        return ergebnis
    }

    fun speichern(mappen: List<UnterlagenMappe>) {
        val array = JSONArray()

        mappen.forEach { mappe ->
            val obj = JSONObject()
            obj.put("id", mappe.id)
            obj.put("name", mappe.name)
            obj.put("dokumentIds", JSONArray(mappe.dokumentIds))
            obj.put("angeforderteUnterlagen", JSONArray(mappe.angeforderteUnterlagen))
            obj.put("anforderungen", JSONArray().apply {
                mappe.anforderungen.forEach { anforderung ->
                    put(JSONObject().apply {
                        put("dokumentenart", anforderung.dokumentenart)
                        put("anzahl", anforderung.anzahl ?: JSONObject.NULL)
                        put("neuesteZuerst", anforderung.neuesteZuerst)
                        put("jahr", anforderung.jahr ?: JSONObject.NULL)
                        put("monatVon", anforderung.monatVon ?: JSONObject.NULL)
                        put("monatBis", anforderung.monatBis ?: JSONObject.NULL)
                        put("namensFilter", anforderung.namensFilter ?: JSONObject.NULL)
                    })
                }
            })
            obj.put("manuelleNachweise", JSONObject().apply {
                mappe.manuelleNachweise.forEach { (art, ids) -> put(art, JSONArray(ids)) }
            })
            array.put(obj)
        }

        // Kein unsicherer Copy-Fallback: Backup bleibt bis zum vollständigen
        // synchronisierten Commit erhalten.
        val atomar = AtomicFile(datei)
        val ausgabe = atomar.startWrite()
        try {
            ausgabe.write(array.toString().toByteArray(Charsets.UTF_8))
            atomar.finishWrite(ausgabe)
        } catch (fehler: Exception) {
            atomar.failWrite(ausgabe)
            throw fehler
        }
    }

    fun neueMappe(name: String): UnterlagenMappe {
        return UnterlagenMappe(
            UUID.randomUUID().toString(),
            name,
            mutableListOf()
        )
    }
}
