package de.dokumentenpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast

class MappenOberflaeche(
    private val activity: Activity,
    private val dokumente: List<Pair<String, String>>,
    private val ocrTexte: Map<String, String>,
    private val dokumentOeffnen: (String) -> Unit,
    private val kategorien: List<String>,
    private val kategorieVon: (String) -> String
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

        val namen = (
            inhalt.map { "📄 ${it.second}" } +
            mappe.angeforderteUnterlagen.map { "📋 $it" }
        ).toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(mappe.name)
            .setMessage(
                if (mappe.angeforderteUnterlagen.isEmpty()) {
                    "Noch keine angeforderten Unterlagen erkannt."
                } else {
                    "ANGEFORDERTE UNTERLAGEN\n\n" +
                    mappe.angeforderteUnterlagen.joinToString("\n") { unterlage ->
                        val vorhanden = inhalt.any { dokument ->
                    val text = ocrTexte[dokument.first] ?: ""
                    val istAnforderungsschreiben =
                        UnterlagenErkennung.angeforderteUnterlagen(text).size >= 2
                    if (istAnforderungsschreiben) return@any false
                            (
                    UnterlagenErkennung.passtZuUnterlage(
                        unterlage,
                        dokument.second + " " + text
                    )
                )
                        }
                        (if (vorhanden) "✓ " else "○ ") + unterlage
                    } +
                    "\n\n✓ = Dateiname passt, ○ = noch nicht zugeordnet"
                }
            )
            .setItems(namen) { _, position ->
                if (position < inhalt.size) {
                    dokumentAktionen(mappe, inhalt[position])
                } else {
                    val index = position - inhalt.size
                    nachweisAnzeigen(
                        mappe,
                        mappe.angeforderteUnterlagen[index]
                    )
                }
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


    private fun nachweisAnzeigen(
        mappe: UnterlagenMappe,
        unterlage: String
    ) {
        val treffer = dokumente.filter { dokument ->
            if (dokument.first !in mappe.dokumentIds) {
                false
            } else {
                val text = ocrTexte[dokument.first] ?: ""
                val istAnforderungsschreiben =
                    UnterlagenErkennung.angeforderteUnterlagen(text).size >= 2

                !istAnforderungsschreiben && (
                    UnterlagenErkennung.passtZuUnterlage(
                        unterlage,
                        dokument.second + " " + text
                    )
                )
            }
        }

        if (treffer.isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(unterlage)
                .setMessage("Noch kein passendes Dokument gefunden.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(activity)
                .setTitle("Gefundene Dokumente: $unterlage")
                .setItems(treffer.map { it.second }.toTypedArray()) { _, position ->
                    dokumentOeffnen(treffer[position].first)
                }
                .setNegativeButton("Zurück", null)
                .show()
        }
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

        val ordner = (
            kategorien + dokumente.map { kategorieVon(it.first) }
        ).distinct().filter { it.isNotBlank() }
            .sorted()

        if (ordner.isNotEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle("Kategorie auswählen")
                .setItems(
                    ordner.map { "📁 $it" }.toTypedArray()
                ) { _, position ->
                    dokumenteAusKategorieHinzufuegen(
                        mappe,
                        ordner[position]
                    )
                }
                .setNegativeButton("Zurück") { _, _ ->
                    mappeOeffnen(mappe)
                }
                .show()
            return
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

    private fun dokumenteAusKategorieHinzufuegen(
        mappe: UnterlagenMappe,
        kategorie: String
    ) {
        val auswahl = dokumente.filter {
            kategorieVon(it.first) == kategorie &&
            it.first !in mappe.dokumentIds
        }

        if (auswahl.isEmpty()) {
            Toast.makeText(
                activity,
                "Keine weiteren Dokumente in dieser Kategorie",
                Toast.LENGTH_SHORT
            ).show()
            dokumenteHinzufuegen(mappe)
            return
        }

        val markiert = BooleanArray(auswahl.size)

        AlertDialog.Builder(activity)
            .setTitle("📁 $kategorie")
            .setMultiChoiceItems(
                auswahl.map { it.second }.toTypedArray(),
                markiert
            ) { _, position, istMarkiert ->
                markiert[position] = istMarkiert
            }
            .setPositiveButton("Hinzufügen") { _, _ ->
                var anzahl = 0

                auswahl.forEachIndexed { index, dokument ->
                    if (markiert[index]) {
                        mappe.dokumentIds.add(dokument.first)
                        anzahl++
                    }
                }

                if (anzahl > 0) {
                    speichern(mappe)
                }

                Toast.makeText(
                    activity,
                    "$anzahl Dokumente hinzugefügt",
                    Toast.LENGTH_SHORT
                ).show()

                mappeOeffnen(mappe)
            }
            .setNegativeButton("Zurück") { _, _ ->
                dokumenteHinzufuegen(mappe)
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
