package de.dokumentenpilot.app

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AssistentOberflaeche(
    private val activity: MainActivity,
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
            text = activity.getString(R.string.ui_text_061)
            textSize = 17f
        }
        layout.addView(hinweis)

        val eingabe = EditText(activity).apply {
            hint = activity.getString(R.string.ui_text_062)
            minLines = 2
        }
        layout.addView(eingabe)

        activity.dialogBuilder()
            .setTitle(activity.getString(R.string.ui_text_063))
            .setView(layout)
            .setPositiveButton(activity.getString(R.string.ui_text_064)) { _, _ ->
                suchen(eingabe.text.toString())
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }

    private fun suchen(text: String) {
        val anfrage = AssistentErkennung.erkennen(text)

        if (anfrage == null) {
            activity.dialogBuilder()
                .setTitle(activity.getString(R.string.ui_text_065))
                .setMessage(
                    "Momentan erkenne ich Anfragen nach " +
                    "Gehaltsabrechnungen. Weitere Unterlagenarten folgen."
                )
                .setPositiveButton(activity.getString(R.string.ui_text_066)) { _, _ ->
                    anzeigen()
                }
                .setNegativeButton(activity.getString(R.string.ui_text_001), null)
                .show()
            return
        }

        val treffer = AssistentSuche.assistentTreffer(dokumente, ocrTexte, anfrage)

        if (treffer.isEmpty()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.ui_text_070),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        activity.dialogBuilder()
            .setTitle(activity.getString(R.string.ui_text_067))
            .setMessage(
                "Angefragt: ${anfrage.beschreibung}\n\n" +
                "Gefunden: ${treffer.size} Dokumente.\n" +
                "Bitte prüfe die Treffer und Abrechnungszeiträume. " +
                "Bei der Auswahl der letzten Monate zählen nur Dokumente mit erkanntem Abrechnungsmonat."
            )
            .setPositiveButton(activity.getString(R.string.ui_text_068)) { _, _ ->
                dokumenteAnzeigen(treffer)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }

    private fun dokumenteAnzeigen(
        treffer: List<Pair<String, String>>
    ) {
        activity.dialogBuilder()
            .setTitle(activity.getString(R.string.ui_text_069))
            .setItems(
                treffer.map { it.second }.toTypedArray()
            ) { _, position ->
                dokumentOeffnen(treffer[position].first)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }
}
