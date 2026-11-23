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
    private val kategorieVon: (String) -> String,
    private val dokumenteTeilen: (List<String>) -> Unit
) {
    private val verwaltung = UnterlagenMappen(activity)

    fun anzeigen() {
        val mappen = verwaltung.laden()
        val namen = mappen.map {
            "📁 ${it.name} (${it.dokumentIds.size})"
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_048))
            .setItems(namen) { _, position ->
                mappeOeffnen(mappen[position])
            }
            .setPositiveButton(activity.getString(R.string.ui_text_049)) { _, _ ->
                val eingabe = EditText(activity)
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.ui_text_049))
                    .setView(eingabe)
                    .setPositiveButton(activity.getString(R.string.ui_text_005)) { _, _ ->
                        val name = eingabe.text.toString().trim()
                        if (name.isNotEmpty()) {
                            mappen.add(verwaltung.neueMappe(name))
                            verwaltung.speichern(mappen)
                            anzeigen()
                        }
                    }
                    .setNegativeButton(activity.getString(R.string.ui_text_000), null)
                    .show()
            }
            .setNegativeButton(activity.getString(R.string.ui_text_001), null)
            .show()
    }

    private fun mappeOeffnen(mappe: UnterlagenMappe) {
        val inhalt = dokumente.filter {
            it.first in mappe.dokumentIds
        }

        val checkliste = mappe.angeforderteUnterlagen.map { unterlage ->
            val vorhanden = inhalt.any { dokument ->
                val text = ocrTexte[dokument.first] ?: ""
                val istAnforderungsschreiben =
                    UnterlagenErkennung.angeforderteUnterlagen(text).size >= 2

                !istAnforderungsschreiben &&
                    UnterlagenErkennung.passtZuUnterlage(
                        unterlage,
                        dokument.second + " " + text
                    )
            }
            (if (vorhanden) "✓ " else "○ ") + unterlage
        }

        val namen = (
            inhalt.map { "📄 ${it.second}" } +
            checkliste.map { "📋 $it" }
        ).toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(mappe.name)
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
            .setPositiveButton(activity.getString(R.string.ui_text_007)) { _, _ ->
                dokumenteHinzufuegen(mappe)
            }
            .setNeutralButton(activity.getString(R.string.ui_text_009)) { _, _ ->
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.ui_text_050))
                    .setItems(
                        arrayOf(
                            "🔎 Passende Unterlagen suchen",
                            "📤 Unterlagen versenden",
                            "⚙️ Mappe verwalten"
                        )
                    ) { _, position ->
                        if (position == 0) {
                            passendeDokumenteSuchen(mappe)
                        } else if (position == 1) {
                            mappeTeilen(mappe)
                        } else {
                            mappenAktionen(mappe)
                        }
                    }
                    .show()
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
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
                .setMessage(activity.getString(R.string.ui_text_051))
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(activity)
                .setTitle("Gefundene Dokumente: $unterlage")
                .setItems(treffer.map { it.second }.toTypedArray()) { _, position ->
                    dokumentOeffnen(treffer[position].first)
                }
                .setNegativeButton(activity.getString(R.string.ui_text_002), null)
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
                        .setTitle(activity.getString(R.string.ui_text_052))
                        .setMessage(
                            "Das Original bleibt im DokumentenPilot-Archiv."
                        )
                        .setPositiveButton(activity.getString(R.string.ui_text_008)) { _, _ ->
                            mappe.dokumentIds.remove(dokument.first)
                            speichern(mappe)
                            mappeOeffnen(mappe)
                        }
                        .setNegativeButton(activity.getString(R.string.ui_text_000), null)
                        .show()
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }


    private fun passendeDokumenteSuchen(mappe: UnterlagenMappe) {
        val passendeAnforderungen =
            mappe.anforderungen.ifEmpty {
                mappe.angeforderteUnterlagen.map { art ->
                    UnterlagenAnforderung(
                        dokumentenart = art,
                        anzahl = null,
                        neuesteZuerst = false
                    )
                }
            }

        val bereitsVorhandeneMonate = passendeAnforderungen.associate { anforderung ->
            val monate = dokumente
                .filter { dokument ->
                    dokument.first in mappe.dokumentIds &&
                        UnterlagenErkennung.passtZuUnterlage(
                            anforderung.dokumentenart,
                            dokument.second + " " +
                                (ocrTexte[dokument.first] ?: "")
                        )
                }
                .mapNotNull { dokument ->
                    val ocr = ocrTexte[dokument.first] ?: ""
                    if (anforderung.dokumentenart == "Gehaltsabrechnungen") {
                        AssistentDatum.abrechnungsmonat(dokument.second, ocr)
                    } else if (anforderung.dokumentenart == "Kontoauszüge") {
                        AssistentDatum.kontoauszugsmonat(dokument.second, ocr)
                    } else {
                        AssistentDatum.dokumentMonat(dokument.second, ocr)
                    }
                }
                .toSet()

            anforderung.dokumentenart to monate
        }

        val fehlendeAnforderungen = passendeAnforderungen.map { anforderung ->
            val vorhandeneDokumente = dokumente.filter { dokument ->
                dokument.first in mappe.dokumentIds &&
                    UnterlagenErkennung.passtZuUnterlage(
                        anforderung.dokumentenart,
                        dokument.second + " " +
                            (ocrTexte[dokument.first] ?: "")
                    )
            }

            val vorhandeneAnzahl = if (anforderung.neuesteZuerst) {
                bereitsVorhandeneMonate[
                    anforderung.dokumentenart
                ]?.size ?: 0
            } else {
                vorhandeneDokumente.size
            }

            anforderung.copy(
                anzahl = anforderung.anzahl?.let {
                    (it - vorhandeneAnzahl).coerceAtLeast(0)
                }
            )
        }

        val fehlendeVorauswahl = AssistentSuche.unterlagenZusammenstellen(
            dokumente = dokumente.filter { dokument ->
                if (dokument.first in mappe.dokumentIds) {
                    false
                } else {
                    passendeAnforderungen.any { anforderung ->
                        val ocr = ocrTexte[dokument.first] ?: ""
                        val monat = if (
                            anforderung.dokumentenart == "Gehaltsabrechnungen"
                        ) {
                            AssistentDatum.abrechnungsmonat(
                                dokument.second, ocr
                            )
                        } else if (anforderung.dokumentenart == "Kontoauszüge") {
                            AssistentDatum.kontoauszugsmonat(
                                dokument.second, ocr
                            )
                        } else {
                            AssistentDatum.dokumentMonat(
                                dokument.second, ocr
                            )
                        }

                        UnterlagenErkennung.passtZuUnterlage(
                            anforderung.dokumentenart,
                            dokument.second + " " + ocr
                        ) && (
                            monat == null ||
                            monat !in (
                                bereitsVorhandeneMonate[
                                    anforderung.dokumentenart
                                ] ?: emptySet()
                            )
                        )
                    }
                }
            },
            ocrTexte = ocrTexte,
            anforderungen = fehlendeAnforderungen
        )

        val treffer = fehlendeVorauswahl

        if (treffer.isEmpty()) {
            Toast.makeText(
                activity,
                "Keine passenden Dokumente gefunden",
                Toast.LENGTH_LONG
            ).show()
            mappeOeffnen(mappe)
            return
        }

        val markiert = BooleanArray(treffer.size) { true }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_053))
            .setMultiChoiceItems(
                treffer.map { it.second }.toTypedArray(),
                markiert
            ) { _, position, ausgewaehlt ->
                markiert[position] = ausgewaehlt
            }
            .setPositiveButton(activity.getString(R.string.ui_text_054)) { _, _ ->
                treffer.forEachIndexed { index, dokument ->
                    if (markiert[index] &&
                        dokument.first !in mappe.dokumentIds
                    ) {
                        mappe.dokumentIds.add(dokument.first)
                    }
                }
                speichern(mappe)
                mappeOeffnen(mappe)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_000)) { _, _ ->
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
                .setTitle(activity.getString(R.string.ui_text_025))
                .setItems(
                    (listOf("📄 Alle Dokumente") + ordner.map { "📁 $it" }).toTypedArray()
                ) { _, position ->
                    if (position == 0) {
                        alleDokumenteHinzufuegen(mappe)
                    } else {
                        dokumenteAusKategorieHinzufuegen(
                            mappe,
                            ordner[position - 1]
                        )
                    }
                }
                .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
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
            .setTitle(activity.getString(R.string.ui_text_055))
            .setMultiChoiceItems(
                verfuegbar.map { it.second }.toTypedArray(),
                ausgewaehlt
            ) { _, position, markiert ->
                ausgewaehlt[position] = markiert
            }
            .setPositiveButton(activity.getString(R.string.ui_text_007)) { _, _ ->
                verfuegbar.forEachIndexed { index, dokument ->
                    if (ausgewaehlt[index]) {
                        mappe.dokumentIds.add(dokument.first)
                    }
                }
                speichern(mappe)
                mappeOeffnen(mappe)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_000)) { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }


    private fun alleDokumenteHinzufuegen(mappe: UnterlagenMappe) {
        val auswahl = dokumente.filter {
            it.first !in mappe.dokumentIds
        }
        if (auswahl.isEmpty()) {
            Toast.makeText(
                activity,
                "Keine weiteren Dokumente vorhanden",
                Toast.LENGTH_SHORT
            ).show()
            mappeOeffnen(mappe)
            return
        }

        val markiert = BooleanArray(auswahl.size)
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_056))
            .setMultiChoiceItems(
                auswahl.map { it.second }.toTypedArray(),
                markiert
            ) { _, position, ausgewaehlt ->
                markiert[position] = ausgewaehlt
            }
            .setPositiveButton(activity.getString(R.string.ui_text_007)) { _, _ ->
                auswahl.forEachIndexed { index, dokument ->
                    if (markiert[index]) {
                        mappe.dokumentIds.add(dokument.first)
                    }
                }
                speichern(mappe)
                mappeOeffnen(mappe)
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
                dokumenteHinzufuegen(mappe)
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
            .setPositiveButton(activity.getString(R.string.ui_text_007)) { _, _ ->
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
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
                dokumenteHinzufuegen(mappe)
            }
            .show()
    }


    private fun mappeTeilen(mappe: UnterlagenMappe) {
        val inhalt = dokumente.filter {
            it.first in mappe.dokumentIds
        }

        if (inhalt.isEmpty()) {
            Toast.makeText(
                activity,
                "Diese Mappe enthält noch keine Dokumente",
                Toast.LENGTH_LONG
            ).show()
            mappeOeffnen(mappe)
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_057))
            .setItems(
                arrayOf(
                    "📁 Komplette Mappe versenden",
                    "📄 Einzelne Unterlagen auswählen"
                )
            ) { _, position ->
                if (position == 0) {
                    dokumenteTeilen(inhalt.map { it.first })
                } else {
                    einzelneDokumenteTeilen(mappe, inhalt)
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
                mappeOeffnen(mappe)
            }
            .show()
    }

    private fun einzelneDokumenteTeilen(
        mappe: UnterlagenMappe,
        inhalt: List<Pair<String, String>>
    ) {
        val markiert = BooleanArray(inhalt.size)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_058))
            .setMultiChoiceItems(
                inhalt.map { it.second }.toTypedArray(),
                markiert
            ) { _, position, ausgewaehlt ->
                markiert[position] = ausgewaehlt
            }
            .setPositiveButton(activity.getString(R.string.ui_text_010)) { _, _ ->
                val auswahl = inhalt.filterIndexed { index, _ ->
                    markiert[index]
                }.map { it.first }

                if (auswahl.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "Bitte mindestens ein Dokument auswählen",
                        Toast.LENGTH_SHORT
                    ).show()
                    mappeOeffnen(mappe)
                } else {
                    dokumenteTeilen(auswahl)
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
                mappeTeilen(mappe)
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
                        .setTitle(activity.getString(R.string.ui_text_059))
                        .setView(eingabe)
                        .setPositiveButton(activity.getString(R.string.ui_text_004)) { _, _ ->
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
                        .setNegativeButton(activity.getString(R.string.ui_text_000), null)
                        .show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.ui_text_060))
                        .setMessage(
                            "Die Mappe wird gelöscht. " +
                            "Deine Originaldokumente bleiben erhalten."
                        )
                        .setPositiveButton(activity.getString(R.string.ui_text_006)) { _, _ ->
                            val mappen = verwaltung.laden()
                            mappen.removeAll { it.id == mappe.id }
                            verwaltung.speichern(mappen)
                            anzeigen()
                        }
                        .setNegativeButton(activity.getString(R.string.ui_text_000), null)
                        .show()
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ ->
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
