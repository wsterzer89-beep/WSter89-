package de.dokumentenpilot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UnterlagenMappe(
    val id: String,
    val name: String,
    val dokumentIds: MutableList<String>,
    val angeforderteUnterlagen: MutableList<String> = mutableListOf(),
    val anforderungen: MutableList<UnterlagenAnforderung> = mutableListOf()
)

class UnterlagenMappen(private val context: Context) {

    private val alt = File(context.filesDir, "unterlagen_mappen.json")
    private val datei = GeschuetzteDatei(File(context.filesDir, "unterlagen_mappen.dp"))

    fun laden(): MutableList<UnterlagenMappe> {
        val text = datei.lesen(alt) {
            if (!alt.exists()) null else alt.readText().also { JSONArray(it) }
        } ?: return mutableListOf()
        File(context.filesDir, "unterlagen_mappen.tmp").delete()

        val ergebnis = mutableListOf<UnterlagenMappe>()
        val array = JSONArray(text)

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
                            }
                        )
                    )
                }
            }

            ergebnis.add(
                UnterlagenMappe(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    dokumentIds = ids,
                    angeforderteUnterlagen = unterlagen,
                    anforderungen = anforderungen
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
                    })
                }
            })
            array.put(obj)
        }

        datei.schreiben(array.toString())
    }

    fun neueMappe(name: String): UnterlagenMappe {
        return UnterlagenMappe(
            UUID.randomUUID().toString(),
            name,
            mutableListOf()
        )
    }
}
