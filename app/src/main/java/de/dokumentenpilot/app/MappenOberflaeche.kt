package de.dokumentenpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast

class MappenOberflaeche(
    private val activity: Activity,
    private val dokumente: List<Pair<String, String>>,
    private val dokumentOeffnen: (String) -> Unit
) {
    private val verwaltung = UnterlagenMappen(activity)

    fun anzeigen() {
        val mappen = verwaltung.laden()
        val namen = mappen.map {
            "📁 ${it.name} (${it.dokumentIds.size})"
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Meine Unterlagenmappen")
            .setItems(namen) { _, position ->
                mappeOeffnen(mappen[position])
            }
            .setPositiveButton("Neue Mappe") { _, _ ->
                val eingabe = EditText(activity)
                AlertDialog.Builder(activity)
                    .setTitle("Neue Mappe")
                    .setView(eingabe)
                    .setPositiveButton("Erstellen") { _, _ ->
                        val name = eingabe.text.toString().trim()
                        if (name.isNotEmpty()) {
                            mappen.add(verwaltung.neueMappe(name))
                            verwaltung.speichern(mappen)
                            anzeigen()
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun mappeOeffnen(mappe: UnterlagenMappe) {
        val inhalt = dokumente.filter {
            it.first in mappe.dokumentIds
        }

        val namen = inhalt.map {
            "📄 ${it.second}"
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(mappe.name)
            .setItems(namen) { _, position ->
                dokumentAktionen(mappe, inhalt[position])
            }
            .setPositiveButton("Hinzufügen") { _, _ ->
                dokumenteHinzufuegen(mappe)
            }
            .setNeutralButton("Verwalten") { _, _ ->
                mappenAktionen(mappe)
            }
            .setNegativeButton("Zurück") { _, _ ->
                anzeigen()
            }
            .show()
    }

    private fun dokumentAktionen(
        mappe: UnterlagenMappe,
        dokument: Pair<String, String>
    ) {
        AlertDialog.Builder(activity)
            .setTitle(dokument.second)
            .setItems(
                arrayOf("📄 Öffnen", "🗑️ Aus Mappe entfernen")
            ) { _, auswahl ->
                if (auswahl == 0) {
                    dokumentOeffnen(dokument.first)
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("Dokument entfernen?")
                        .setMessage(
                            "Das Original bleibt im DokumentenPilot-Archiv."
                        )
                        .setPositiveButton("Entfernen") { _, _ ->
                            mappe.dokumentIds.remove(dokument.first)
                            speichern(mappe)
                            mappeOeffnen(mappe)
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .setNegativeButton("Zurück") { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }

    private fun dokumenteHinzufuegen(mappe: UnterlagenMappe) {
        val verfuegbar = dokumente.filter {
            it.first !in mappe.dokumentIds
        }

        if (verfuegbar.isEmpty()) {
            Toast.makeText(
                activity,
                "Keine weiteren Dokumente vorhanden",
                Toast.LENGTH_SHORT
            ).show()
            mappeOeffnen(mappe)
            return
        }

        val ausgewaehlt = BooleanArray(verfuegbar.size)

        AlertDialog.Builder(activity)
            .setTitle("Dokumente auswählen")
            .setMultiChoiceItems(
                verfuegbar.map { it.second }.toTypedArray(),
                ausgewaehlt
            ) { _, position, markiert ->
                ausgewaehlt[position] = markiert
            }
            .setPositiveButton("Hinzufügen") { _, _ ->
                verfuegbar.forEachIndexed { index, dokument ->
                    if (ausgewaehlt[index]) {
                        mappe.dokumentIds.add(dokument.first)
                    }
                }
                speichern(mappe)
                mappeOeffnen(mappe)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }

    private fun mappenAktionen(mappe: UnterlagenMappe) {
        AlertDialog.Builder(activity)
            .setTitle(mappe.name)
            .setItems(
                arrayOf("✏️ Umbenennen", "🗑️ Mappe löschen")
            ) { _, auswahl ->
                if (auswahl == 0) {
                    val eingabe = EditText(activity)
                    eingabe.setText(mappe.name)

                    AlertDialog.Builder(activity)
                        .setTitle("Mappe umbenennen")
                        .setView(eingabe)
                        .setPositiveButton("Speichern") { _, _ ->
                            val neuerName = eingabe.text.toString().trim()
                            if (neuerName.isNotEmpty()) {
                                val mappen = verwaltung.laden()
                                val index = mappen.indexOfFirst {
                                    it.id == mappe.id
                                }
                                if (index >= 0) {
                                    mappen[index] = mappe.copy(name = neuerName)
                                    verwaltung.speichern(mappen)
                                }
                                anzeigen()
                            }
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("Mappe wirklich löschen?")
                        .setMessage(
                            "Die Mappe wird gelöscht. " +
                            "Deine Originaldokumente bleiben erhalten."
                        )
                        .setPositiveButton("Löschen") { _, _ ->
                            val mappen = verwaltung.laden()
                            mappen.removeAll { it.id == mappe.id }
                            verwaltung.speichern(mappen)
                            anzeigen()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .setNegativeButton("Zurück") { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }

    private fun speichern(mappe: UnterlagenMappe) {
        val mappen = verwaltung.laden()
        val index = mappen.indexOfFirst { it.id == mappe.id }
        if (index >= 0) {
            mappen[index] = mappe
            verwaltung.speichern(mappen)
        }
    }
}
