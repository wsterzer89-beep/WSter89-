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
    val angeforderteUnterlagen: MutableList<String> = mutableListOf()
)

class UnterlagenMappen(private val context: Context) {

    private val datei = File(context.filesDir, "unterlagen_mappen.json")

    fun laden(): MutableList<UnterlagenMappe> {
        if (!datei.exists()) return mutableListOf()

        val ergebnis = mutableListOf<UnterlagenMappe>()
        val array = JSONArray(datei.readText())

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val ids = mutableListOf<String>()
            val dokumente = obj.getJSONArray("dokumentIds")

            for (j in 0 until dokumente.length()) {
                ids.add(dokumente.getString(j))
            }

            ergebnis.add(
                UnterlagenMappe(
                    obj.getString("id"),
                    obj.getString("name"),
                    ids,
                    mutableListOf<String>().apply {
                        val liste = obj.optJSONArray("angeforderteUnterlagen")
                        if (liste != null) {
                            for (k in 0 until liste.length()) {
                                add(liste.getString(k))
                            }
                        }
                    }
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
            array.put(obj)
        }

        val temp = File(context.filesDir, "unterlagen_mappen.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(datei)) {
            temp.copyTo(datei, overwrite = true)
            temp.delete()
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
