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
    private val dokumentOeffnen: (String) -> Unit,
    private val dokumenteTeilen: (List<String>) -> Unit
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

        val listenpunkte = UnterlagenErkennung.listenpunkteErkennen(text)
        val anforderungen = if (listenpunkte.isEmpty()) UnterlagenErkennung.anforderungenErkennen(text)
            else listenpunkte.flatMap { it.anforderungen }
        val gefunden = if (listenpunkte.isEmpty()) AssistentSuche.assistentTreffer(dokumente, ocrTexte, anfrage)
            else AssistentSuche.unterlagenZusammenstellen(dokumente, ocrTexte, anforderungen)
        if (anforderungen.isEmpty() && listenpunkte.isEmpty()) {
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
            val gruppen = if (listenpunkte.isEmpty()) listOf(
                UnterlagenErkennung.Listenpunkt("", anforderungen, false, false)
            ) else listenpunkte
            for (punkt in gruppen) {
                if (punkt.original.isNotBlank()) {
                    append(punkt.original)
                    append("\n")
                }
                val eindeutigeAlternative = UnterlagenErkennung.alternativeGruppen(punkt) != null
                val alternativeErfuellt = UnterlagenErkennung.alternativeGruppenErfuellt(punkt) {
                    AssistentSuche.unterlagenZusammenstellen(dokumente, ocrTexte, listOf(it)).size
                }
                if (eindeutigeAlternative && !alternativeErfuellt) {
                    append(activity.getString(R.string.assistant_missing_label))
                    append(": 1\n")
                }
                if (punkt.anforderungen.isEmpty() || punkt.bedingt || punkt.spaeter || (punkt.alternative && !eindeutigeAlternative)) {
                    append(activity.getString(R.string.assistant_uncertain_label))
                    append("\n")
                }
                for (anforderung in punkt.anforderungen) {
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
                    if (fehlend > 0 && !punkt.bedingt && !punkt.spaeter && !punkt.alternative) {
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
                    if (anforderung.monatVon != null && anforderung.monatBis != null) {
                        append(activity.getString(R.string.assistant_period_label))
                        append(": ")
                        append(formatMonat(anforderung.monatVon))
                        append(" – ")
                        append(formatMonat(anforderung.monatBis))
                        append("\n")
                    }
                    anforderung.namensFilter?.let {
                        append("🔎 ")
                        append(it)
                        append("\n")
                    }
                    treffer.forEach { dokument ->
                        append("• ")
                        append(dokument.second)
    
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

    private fun formatMonat(monat: Int): String =
        (monat % 100).toString().padStart(2, '0') + "/" + (monat / 100)

    private fun dokumenteAnzeigen(treffer: List<Pair<String, String>>) {
        val unique = treffer.distinctBy { it.first }
        val selection = AssistentAuswahl(unique.map { it.first })
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val count = TextView(activity)
        content.addView(count)
        val boxes = mutableListOf<android.widget.CheckBox>()
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_069))
            .setView(android.widget.ScrollView(activity).apply { addView(content) })
            .setPositiveButton(activity.getString(R.string.assistant_add_folder), null)
            .setNeutralButton(activity.getString(R.string.assistant_share_selected), null)
            .setNegativeButton(activity.getString(R.string.ui_text_001), null).create()
        fun refresh() {
            count.text = activity.getString(R.string.assistant_selection_count, selection.ids().size, unique.size)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = selection.ids().isNotEmpty()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = selection.ids().isNotEmpty()
        }
        val all = android.widget.Button(activity).apply {
            setText(R.string.assistant_select_all)
            setOnClickListener { boxes.forEach { it.isChecked = true } }
        }
        val none = android.widget.Button(activity).apply {
            setText(R.string.assistant_clear_selection)
            setOnClickListener { boxes.forEach { it.isChecked = false } }
        }
        content.addView(all)
        content.addView(none)
        unique.forEach { doc ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val check = android.widget.CheckBox(activity).apply {
                text = doc.second
                setOnCheckedChangeListener { _, checked -> selection.set(doc.first, checked); refresh() }
            }
            boxes.add(check)
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(android.widget.Button(activity).apply {
                setText(R.string.assistant_preview)
                setOnClickListener { dokumentOeffnen(doc.first) }
            })
            content.addView(row)
        }
        dialog.setOnShowListener {
            refresh()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ids = selection.ids()
                if (ids.isNotEmpty()) { dialog.dismiss(); dokumenteFuerMappeAuswaehlen(ids) }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val ids = selection.ids()
                if (ids.isNotEmpty()) {
                    AlertDialog.Builder(activity).setMessage(activity.getString(R.string.assistant_confirm_share, ids.size))
                        .setPositiveButton(R.string.assistant_share_selected) { _, _ -> dialog.dismiss(); dokumenteTeilen(ids) }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
        }
        dialog.show()
    }

    private fun dokumenteFuerMappeAuswaehlen(ids: List<String>) {
        if (ids.isEmpty()) return
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
}
