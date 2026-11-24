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
        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.ui_text_061)
            textSize = 17f
        })
        val eingabe = EditText(activity).apply {
            hint = activity.getString(R.string.ui_text_062)
            minLines = 2
        }
        layout.addView(eingabe)
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_063))
            .setView(layout)
            .setPositiveButton(activity.getString(R.string.ui_text_064)) { _, _ ->
                suchen(eingabe.text.toString())
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }

    private fun monat(art: String, dokument: Pair<String, String>): Int? {
        val ocr = ocrTexte[dokument.first].orEmpty()
        return when (art) {
            "Gehaltsabrechnungen" -> AssistentDatum.abrechnungsmonat(dokument.second, ocr)
            "Kontoauszüge" -> AssistentDatum.kontoauszugsmonat(dokument.second, ocr)
            else -> AssistentDatum.dokumentMonat(dokument.second, ocr)
        }
    }

    private fun suchen(text: String) {
        val anfrage = AssistentErkennung.erkennen(text)
        if (anfrage == null) {
            Toast.makeText(activity, activity.getString(R.string.ui_text_070), Toast.LENGTH_LONG).show()
            return
        }

        val anforderungen = UnterlagenErkennung.anforderungenErkennen(text)
        val gefunden = AssistentSuche.assistentTreffer(dokumente, ocrTexte, anfrage)
        if (anforderungen.isEmpty()) {
            if (gefunden.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.assistant_not_found), Toast.LENGTH_LONG).show()
            } else {
                dokumenteAnzeigen(gefunden)
            }
            return
        }

        val zusammenfassung = buildString {
            append(activity.getString(R.string.assistant_requested_label))
            append(": ")
            append(text.trim())
            append("\n\n")
            for (anforderung in anforderungen) {
                val treffer = AssistentSuche.unterlagenZusammenstellen(
                    dokumente, ocrTexte, listOf(anforderung)
                )
                append(anforderung.dokumentenart)
                append("\n")
                append(activity.getString(R.string.assistant_found_label))
                append(": ")
                append(treffer.size)
                anforderung.anzahl?.let {
                    append(" / ")
                    append(it)
                }
                append("\n")
                val fehlend = anforderung.anzahl?.let { (it - treffer.size).coerceAtLeast(0) }
                    ?: if (treffer.isEmpty()) 1 else 0
                if (fehlend > 0) {
                    append(activity.getString(R.string.assistant_missing_label))
                    append(": ")
                    append(fehlend)
                    append("\n")
                }
                anforderung.jahr?.let {
                    append(activity.getString(R.string.assistant_period_label))
                    append(": ")
                    append(it)
                    append("\n")
                }
                treffer.forEach { dokument ->
                    append("• ")
                    append(dokument.second)
                    append("\n  ")
                    append(activity.getString(R.string.assistant_reason_label))
                    val periode = monat(anforderung.dokumentenart, dokument)
                    if (periode != null) {
                        append("\n  ")
                        append(activity.getString(R.string.assistant_period_label))
                        append(": ")
                        append(periode / 100)
                        append("-")
                        append(periode % 100)
                    } else if (anforderung.dokumentenart == "Kontoauszüge" ||
                        anforderung.dokumentenart == "Gehaltsabrechnungen") {
                        append("\n  ")
                        append(activity.getString(R.string.assistant_uncertain_label))
                    }
                    append("\n")
                }
                append("\n")
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_067))
            .setMessage(zusammenfassung)
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
        if (gefunden.isNotEmpty()) {
            dialog.setPositiveButton(activity.getString(R.string.assistant_select_docs)) { _, _ ->
                dokumenteAnzeigen(gefunden)
            }
        }
        dialog.show()
    }

    private fun dokumenteAnzeigen(treffer: List<Pair<String, String>>) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_069))
            .setItems(treffer.map { it.second }.toTypedArray()) { _, position ->
                dokumentOeffnen(treffer[position].first)
            }
            .setPositiveButton(activity.getString(R.string.assistant_add_folder)) { _, _ ->
                dokumenteFuerMappeAuswaehlen(treffer)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }

    private fun dokumenteFuerMappeAuswaehlen(treffer: List<Pair<String, String>>) {
        val ausgewaehlt = BooleanArray(treffer.size) { true }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.assistant_select_docs))
            .setMultiChoiceItems(treffer.map { it.second }.toTypedArray(), ausgewaehlt) {
                _, position, istAusgewaehlt -> ausgewaehlt[position] = istAusgewaehlt
            }
            .setPositiveButton(activity.getString(R.string.assistant_add_folder)) { _, _ ->
                val ids = treffer.filterIndexed { index, _ -> ausgewaehlt[index] }
                    .map { it.first }.toSet()
                if (ids.isEmpty()) return@setPositiveButton
                val verwaltung = UnterlagenMappen(activity)
                val mappen = verwaltung.laden()
                if (mappen.isEmpty()) {
                    Toast.makeText(activity, activity.getString(R.string.assistant_no_folders),
                        Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.assistant_add_folder))
                        .setItems(mappen.map { it.name }.toTypedArray()) { _, position ->
                            val mappe = mappen[position]
                            ids.forEach { id ->
                                if (id !in mappe.dokumentIds) mappe.dokumentIds.add(id)
                            }
                            try {
                                verwaltung.speichern(mappen)
                                Toast.makeText(activity, activity.getString(R.string.assistant_added),
                                    Toast.LENGTH_LONG).show()
                            } catch (fehler: Exception) {
                                Toast.makeText(activity, activity.getString(R.string.ui_text_044),
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton(activity.getString(R.string.ui_text_001), null)
                        .show()
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }
}
