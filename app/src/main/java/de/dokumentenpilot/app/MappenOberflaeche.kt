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
    private val dokumenteTeilen: (List<String>) -> Unit,
    private val privateDokumenteBereinigen: (List<String>) -> Unit = {},
    private val zugriffErlaubt: () -> Boolean = { false }
) {
    private val verwaltung = UnterlagenMappen(activity)
    private fun archivDokumente() = MappenImportRegeln.nachweise(dokumente, kategorieVon)

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
        val ids = mappe.dokumentIds.toSet()
        val inhalt = dokumente.filter { it.first in ids }.distinctBy { it.first }
            .sortedByDescending { kategorieVon(it.first) == MappenImportRegeln.KATEGORIE }

        val nachweise = MappenImportRegeln.nachweise(inhalt, kategorieVon)
        val anforderungen = anforderungenFuer(mappe)
        val texte = ocrTexte.toMap()
        val dokumentNamen = inhalt.map { (if (kategorieVon(it.first) == MappenImportRegeln.KATEGORIE) "📋 Suchgrundlage: " else "📄 ") + it.second }
        var nachweisTreffer: List<List<Pair<String, String>>>? = null
        val namen = (dokumentNamen +
            anforderungen.map { "… ${it.dokumentenart}" }).toMutableList()
        val adapter = android.widget.ArrayAdapter(activity, android.R.layout.simple_list_item_1, namen)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(mappe.name)
            .setAdapter(adapter) { _, position ->
                if (!zugriffErlaubt()) return@setAdapter
                if (position < inhalt.size) {
                    dokumentAktionen(mappe, inhalt[position])
                } else {
                    val index = position - inhalt.size
                    nachweisAnzeigen(
                        mappe,
                        anforderungen[index],
                        nachweisTreffer?.get(index)
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
        if (anforderungen.isEmpty()) return
        val arbeit = Thread {
            val vorlagenIds = mutableSetOf<String>()
            val ergebnis = runCatching {
                val cache = mutableMapOf<String, Boolean>()
                nachweise.forEach { dokument ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (cache.getOrPut(dokument.first) { UnterlagenSuche.istAnforderungsschreiben(dokument.second, texte[dokument.first].orEmpty()) }) vorlagenIds.add(dokument.first)
                }
                val artenCache = mutableMapOf<String, Set<String>>()
                anforderungen.map { anforderung ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val treffer = manuelleTreffer(mappe, anforderung, nachweise, texte, cache) + AssistentSuche.unterlagenZusammenstellen(
                        nachweise.filter { mappe.nachweisErlaubt(anforderung.dokumentenart, it.first) }, texte, listOf(anforderung), cache, artenCache
                    ).filterNot { it.first in mappe.manuelleNachweise[anforderung.dokumentenart].orEmpty() }
                    val vorhanden = treffer.size
                    val benoetigt = anforderung.anzahl ?: 1
                    val status = if (vorhanden >= benoetigt) "✓" else "○"
                    val anzahl = if (anforderung.anzahl != null) " ($vorhanden/$benoetigt)" else ""
                    "$status ${anforderung.dokumentenart}$anzahl" to treffer
                }
            }
            activity.runOnUiThread {
                if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (!zugriffErlaubt()) {
                    dialog.dismiss()
                    return@runOnUiThread
                }
                nachweisTreffer = ergebnis.getOrNull()?.map { it.second }
                adapter.clear()
                adapter.addAll(inhalt.mapIndexed { index, dokument ->
                    if (dokument.first in vorlagenIds) "📋 Suchgrundlage: ${dokument.second}" else dokumentNamen[index]
                } +
                    (ergebnis.getOrNull()?.map { it.first } ?: anforderungen.map { a -> "? ${a.dokumentenart}" }).map { "📋 $it" })
            }
        }
        dialog.setOnDismissListener { arbeit.interrupt() }
        arbeit.start()
    }

    private fun nachweisAnzeigen(
        mappe: UnterlagenMappe,
        anforderung: UnterlagenAnforderung,
        berechneteTreffer: List<Pair<String, String>>? = null
    ) {
        val unterlage = anforderung.dokumentenart
        if (berechneteTreffer != null) {
            nachweisErgebnisAnzeigen(mappe, anforderung, berechneteTreffer)
            return
        }
        val nachweise = archivDokumente().filter { it.first in mappe.dokumentIds }
        val texte = ocrTexte.toMap()
        val dialog = AlertDialog.Builder(activity)
            .setTitle(unterlage)
            .setView(android.widget.ProgressBar(activity))
            .setNegativeButton(activity.getString(R.string.ui_text_000), null)
            .create()
        dialog.show()
        val arbeit = Thread {
            val ergebnis = runCatching {
                (manuelleTreffer(mappe, anforderung, nachweise, texte, mutableMapOf()) + AssistentSuche.unterlagenZusammenstellen(nachweise.filter { mappe.nachweisErlaubt(unterlage, it.first) }, texte, listOf(anforderung))).distinctBy { it.first }
            }
            activity.runOnUiThread {
                if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                dialog.dismiss()
                if (!zugriffErlaubt()) return@runOnUiThread
                ergebnis.onSuccess { nachweisErgebnisAnzeigen(mappe, anforderung, it) }
                    .onFailure { Toast.makeText(activity, activity.getString(R.string.ui_text_045), Toast.LENGTH_LONG).show() }
            }
        }
        dialog.setOnDismissListener { arbeit.interrupt() }
        arbeit.start()
    }

    private fun manuelleTreffer(
        mappe: UnterlagenMappe, anforderung: UnterlagenAnforderung,
        nachweise: List<Pair<String, String>>, texte: Map<String, String>,
        cache: MutableMap<String, Boolean>
    ): List<Pair<String, String>> = nachweise.filter { dokument ->
        mappe.nachweisErlaubt(anforderung.dokumentenart, dokument.first) &&
            dokument.first in mappe.manuelleNachweise[anforderung.dokumentenart].orEmpty() &&
            !cache.getOrPut(dokument.first) {
                UnterlagenSuche.istAnforderungsschreiben(dokument.second, texte[dokument.first].orEmpty())
            }
    }.distinctBy { it.first }

    private fun nachweisErgebnisAnzeigen(mappe: UnterlagenMappe, anforderung: UnterlagenAnforderung, treffer: List<Pair<String, String>>) {
        if (!zugriffErlaubt()) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(anforderung.dokumentenart)
            .setNeutralButton("Nachweis zuordnen") { _, _ -> nachweisManuellZuordnen(mappe, anforderung) }
            .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ -> mappeOeffnen(mappe) }
        if (treffer.isEmpty()) dialog.setMessage(activity.getString(R.string.ui_text_051))
        else dialog.setItems(treffer.map { it.second }.toTypedArray()) { _, position ->
            if (!zugriffErlaubt()) return@setItems
            val dokument = treffer[position]
            AlertDialog.Builder(activity)
                .setTitle(dokument.second)
                .setItems(arrayOf("Öffnen", "Zuordnung entfernen")) aktion@{ _, aktion ->
                    if (!zugriffErlaubt()) return@aktion
                    if (aktion == 0) dokumentOeffnen(dokument.first)
                    else AlertDialog.Builder(activity)
                        .setTitle("Zuordnung entfernen?")
                        .setMessage("Nur diese Checklisten-Zuordnung wird entfernt. Das Dokument bleibt in der Mappe und im Archiv.")
                        .setPositiveButton("Entfernen") { _, _ ->
                            if (zugriffErlaubt()) {
                                mappe.nachweisEntfernen(anforderung.dokumentenart, dokument.first)
                                speichern(mappe)
                                mappeOeffnen(mappe)
                            }
                        }
                        .setNegativeButton(activity.getString(R.string.ui_text_000)) { _, _ -> nachweisAnzeigen(mappe, anforderung) }
                        .show()
                }
                .setNegativeButton(activity.getString(R.string.ui_text_002)) { _, _ -> nachweisAnzeigen(mappe, anforderung) }
                .show()
        }
        dialog.show()
    }

    private fun nachweisManuellZuordnen(mappe: UnterlagenMappe, anforderung: UnterlagenAnforderung) {
        if (!zugriffErlaubt()) return
        val kandidaten = archivDokumente().filter { it.first in mappe.dokumentIds }.distinctBy { it.first }
        val bisher = mappe.manuelleNachweise[anforderung.dokumentenart].orEmpty()
        val markiert = BooleanArray(kandidaten.size) { kandidaten[it].first in bisher }
        AlertDialog.Builder(activity)
            .setTitle(anforderung.dokumentenart)
            .setMultiChoiceItems(kandidaten.map { it.second }.toTypedArray(), markiert) { _, index, checked ->
                markiert[index] = checked
            }
            .setPositiveButton(activity.getString(R.string.ui_text_005)) { _, _ ->
                if (zugriffErlaubt()) {
                    mappe.nachweiseZuordnen(anforderung.dokumentenart,
                        kandidaten.filterIndexed { index, _ -> markiert[index] }.map { it.first })
                    speichern(mappe)
                    mappeOeffnen(mappe)
                }
            }
            .setNegativeButton(activity.getString(R.string.ui_text_000)) { _, _ -> mappeOeffnen(mappe) }
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
                        .setTitle(activity.getString(R.string.ui_text_052))
                        .setMessage(
                            "Das Original bleibt im DokumentenPilot-Archiv."
                        )
                        .setPositiveButton(activity.getString(R.string.ui_text_008)) { _, _ ->
                            mappe.dokumentIds.remove(dokument.first)
                            mappe.manuelleNachweise.values.forEach { it.removeAll { id -> id == dokument.first } }
                            mappe.ausgeschlosseneNachweise.values.forEach { it.removeAll { id -> id == dokument.first } }
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



    private fun anforderungenFuer(mappe: UnterlagenMappe): List<UnterlagenAnforderung> =
        (mappe.angeforderteUnterlagen + mappe.anforderungen.map { it.dokumentenart })
            .distinct()
            .map { art ->
                val anforderung = mappe.anforderungen.find { it.dokumentenart == art }
                    ?: UnterlagenAnforderung(art, null, false)
                if (art == "Gehaltsabrechnungen") {
                    anforderung.copy(anzahl = anforderung.anzahl ?: 3, neuesteZuerst = true)
                } else {
                    anforderung
                }
            }

    private fun passendeDokumenteSuchen(mappe: UnterlagenMappe) {
        val ids = mappe.dokumentIds.toSet()
        val (inhalt, archiv) = archivDokumente().partition { it.first in ids }
        val anforderungen = anforderungenFuer(mappe)
        val texte = ocrTexte.toMap()
        val fortschritt = android.widget.ProgressBar(activity)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.ui_text_053))
            .setView(fortschritt)
            .setNegativeButton(activity.getString(R.string.ui_text_000), null)
            .create()
        dialog.show()
        val arbeit = Thread {
            val ergebnis = runCatching {
                val cache = mutableMapOf<String, Boolean>()
                val artenCache = mutableMapOf<String, Set<String>>()
                anforderungen.flatMap { anforderung ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    AssistentSuche.fehlendeUnterlagen(inhalt.filter { mappe.nachweisErlaubt(anforderung.dokumentenart, it.first) }, archiv.filter { mappe.nachweisErlaubt(anforderung.dokumentenart, it.first) }, texte, anforderung, cache, artenCache)
                }.distinctBy { it.first }
            }
            activity.runOnUiThread {
                if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                dialog.dismiss()
                if (!zugriffErlaubt()) return@runOnUiThread
                ergebnis.onSuccess { suchErgebnisAnzeigen(mappe, it) }
                    .onFailure { Toast.makeText(activity, activity.getString(R.string.ui_text_045), Toast.LENGTH_LONG).show() }
            }
        }
        dialog.setOnDismissListener { arbeit.interrupt() }
        arbeit.start()
    }

    private fun suchErgebnisAnzeigen(mappe: UnterlagenMappe, treffer: List<Pair<String, String>>) {

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
        val verfuegbar = archivDokumente().filter {
            it.first !in mappe.dokumentIds
        }

        val ordner = (
            kategorien + archivDokumente().map { kategorieVon(it.first) }
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
        val auswahl = archivDokumente().filter {
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
        val auswahl = archivDokumente().filter {
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
                            privateDokumenteBereinigen(mappe.dokumentIds.filter { id ->
                                mappen.none { id in it.dokumentIds }
                            })
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
