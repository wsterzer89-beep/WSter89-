package de.dokumentenpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AssistentOberflaeche(
    private val activity: Activity,
    private val dokumente: List<Pair<String, String>>,
    private val ocrTexte: Map<String, String>,
    private val dokumentOeffnen: (String) -> Unit
) {
    fun anzeigen() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val hinweis = TextView(activity).apply {
            text = "Welche Unterlagen benötigst du?"
            textSize = 17f
        }
        layout.addView(hinweis)

        val eingabe = EditText(activity).apply {
            hint = "z. B. Die letzten 3 Gehaltsabrechnungen"
            minLines = 2
        }
        layout.addView(eingabe)

        AlertDialog.Builder(activity)
            .setTitle("🤖 Unterlagen-Assistent")
            .setView(layout)
            .setPositiveButton("Unterlagen suchen") { _, _ ->
                suchen(eingabe.text.toString())
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun suchen(text: String) {
        val anfrage = AssistentErkennung.erkennen(text)

        if (anfrage == null) {
            AlertDialog.Builder(activity)
                .setTitle("Anfrage noch nicht erkannt")
                .setMessage(
                    "Momentan erkenne ich Anfragen nach " +
                    "Gehaltsabrechnungen. Weitere Unterlagenarten folgen."
                )
                .setPositiveButton("Erneut versuchen") { _, _ ->
                    anzeigen()
                }
                .setNegativeButton("Schließen", null)
                .show()
            return
        }

        val alleTreffer = AssistentSuche.freieSuche(
            dokumente,
            ocrTexte,
            anfrage.beschreibung
        )

        val treffer = if (anfrage.anzahl != null) {
            alleTreffer.take(anfrage.anzahl)
        } else {
            alleTreffer
        }

        if (treffer.isEmpty()) {
            Toast.makeText(
                activity,
                "Keine passenden Dokumente gefunden",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Gefundene Unterlagen")
            .setMessage(
                "Angefragt: ${anfrage.beschreibung}\n\n" +
                "Gefunden: ${treffer.size} Dokumente.\n" +
                "Bitte prüfe die Treffer. " +
                "Eine Datumssortierung folgt noch."
            )
            .setPositiveButton("Dokumente ansehen") { _, _ ->
                dokumenteAnzeigen(treffer)
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun dokumenteAnzeigen(
        treffer: List<Pair<String, String>>
    ) {
        AlertDialog.Builder(activity)
            .setTitle("Passende Dokumente")
            .setItems(
                treffer.map { it.second }.toTypedArray()
            ) { _, position ->
                dokumentOeffnen(treffer[position].first)
            }
            .setNegativeButton("Schließen", null)
            .show()
    }
}
