
package de.dokumentenpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.WindowManager
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {

    private val PDF_AUSWAHL = 100
    private val SCAN_AUSWAHL = 101
    private var aktiveAnfrageMappeId: String? = null

    private var entsperrt = false
    private var anmeldungLaeuft = false
    private var pinAnmeldungLaeuft = false
    private var appImVordergrund = false
    private var scannerGestartet = false
    private var scannerStartZeit = 0L
    private var pdfVorschauGestartet = false
    private var pdfVorschauZeit = 0L
    private var ausstehendesErgebnis: Triple<Int, Int, Intent?>? = null

    // Unverändert: Schlüssel für bestehende Dokumente
    private val schluesselName = "DokumentenPilotSchluessel"

    private val dokumente = mutableListOf<Pair<String, String>>()

    private val standardKategorien = listOf(
        "Allgemein",
        "Arbeit",
        "Bank & Kredite",
        "Wohnen",
        "Versicherungen",
        "Familie & Behörden"
    )

    private val kategorien = mutableListOf<String>()

    private val dunkelblau = Color.rgb(25, 55, 110)
    private val hellblau = Color.rgb(240, 245, 255)

    private lateinit var dokumentenListe: LinearLayout
    private lateinit var anzahlAnzeige: TextView
    private lateinit var suche: EditText
    private lateinit var ocrAnzeige: TextView
    private lateinit var ocrButton: Button

    private var importKategorie = "Allgemein"
    private var aktiveKategorie = "Alle Kategorien"
    private var startseiteAktiv = true

    // OCR-Texte liegen nur während der Nutzung
    // entschlüsselt im Arbeitsspeicher.
    private val ocrTexte = mutableMapOf<String, String>()
    private var ocrLaeuft = false
    private var ocrGeneration = 0

    private val einstellungen by lazy {
        getSharedPreferences(
            "dokument_kategorien",
            MODE_PRIVATE
        )
    }

    private val kategorieEinstellungen by lazy {
        getSharedPreferences(
            "eigene_kategorien",
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        kategorienLaden()
        dokumenteLaden()
        sperrbildschirmAnzeigen()
    }

    override fun onResume() {
        super.onResume()
        appImVordergrund = true
        if (scannerGestartet && scannerStartZeit > 0L) {
            val dauer = android.os.SystemClock.elapsedRealtime() - scannerStartZeit
            if (dauer > 120000L) {
                scannerGestartet = false
                scannerStartZeit = 0L
                entsperrt = false
                ++ocrGeneration
                ocrTexte.clear()
                sperrbildschirmAnzeigen()
            }
        }
        if (pdfVorschauGestartet) {
            val dauer = android.os.SystemClock.elapsedRealtime() - pdfVorschauZeit
            pdfVorschauGestartet = false
            if (pdfVorschauZeit > 0L && dauer > 120000L) {
                entsperrt = false
                ++ocrGeneration
                ocrTexte.clear()
                sperrbildschirmAnzeigen()
            }
        }
        if (!entsperrt && !anmeldungLaeuft && !pinAnmeldungLaeuft) {
            window.decorView.post { anmeldungStarten() }
        }
    }

    override fun onStop() {
        super.onStop()
        appImVordergrund = false

        if (scannerGestartet) {
            scannerStartZeit = android.os.SystemClock.elapsedRealtime()
            return
        }
        if (pdfVorschauGestartet) {
            pdfVorschauZeit = android.os.SystemClock.elapsedRealtime()
            return
        }

        entsperrt = false
        anmeldungLaeuft = false
        ++ocrGeneration
        ocrTexte.clear()
        sperrbildschirmAnzeigen()
    }

    private fun sperrbildschirmAnzeigen() {
        val layout = grundLayout()
        layout.gravity = Gravity.CENTER
        layout.addView(titelText("DokumentenPilot 🔐"))
        layout.addView(TextView(this).apply {
            text = "Deine Dokumente sind gesperrt."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        })
        layout.addView(dunkelblauerButton("ENTSPERREN") {
            anmeldungStarten()
        })
        setContentView(layout)
    }

    private fun anmeldungStarten() {
        if (entsperrt || anmeldungLaeuft || pinAnmeldungLaeuft || !appImVordergrund) return
        anmeldungLaeuft = true
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            pinAnmeldungLaeuft = true
        }
        AppSperre.entsperren(
            this,
            beiErfolg = {
                anmeldungLaeuft = false
                pinAnmeldungLaeuft = false
                if (!appImVordergrund) return@entsperren
                entsperrt = true
                if (startseiteAktiv) {
                    startseiteAnzeigen()
                } else {
                    dokumentenAnsichtAnzeigen(aktiveKategorie)
                }
                ausstehendesErgebnis?.let { ergebnis ->
                    ausstehendesErgebnis = null
                    ergebnisVerarbeiten(ergebnis.first, ergebnis.second, ergebnis.third)
                }
            },
            beiAbbruch = {
                anmeldungLaeuft = false
                pinAnmeldungLaeuft = false
                if (!entsperrt) sperrbildschirmAnzeigen()
            }
        )
        // Auf Android 10 und älter verarbeitet onActivityResult die PIN-Anmeldung.
        if (pinAnmeldungLaeuft) anmeldungLaeuft = false
    }

    private fun dp(wert: Int): Int {
        return (wert * resources.displayMetrics.density).toInt()
    }

    private fun hintergrund(
        farbe: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(farbe)
            cornerRadius = dp(radius.toInt()).toFloat()
        }
    }

    private fun grundLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(90))
            setBackgroundColor(hellblau)
        }
    }

    private fun titelText(beschriftung: String): TextView {
        return TextView(this).apply {
            text = beschriftung
            textSize = 27f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
    }

    private fun dunkelblauerButton(
        beschriftung: String,
        aktion: () -> Unit
    ): Button {
        return Button(this).apply {
            text = beschriftung
            textSize = 13f
            setTextColor(Color.WHITE)
            backgroundTintList =
                ColorStateList.valueOf(dunkelblau)
            setOnClickListener { aktion() }
        }
    }

    private fun meldung(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    // KATEGORIEN

    private fun kategorienLaden() {
        kategorien.clear()

        val gespeichert = kategorieEinstellungen
            .getString("liste", null)

        if (gespeichert == null) {
            kategorien.addAll(standardKategorien)
            kategorienSpeichern()
        } else {
            kategorien.addAll(
                gespeichert.split("\n")
                    .filter { it.isNotBlank() }
                    .distinct()
            )

            if (!kategorien.contains("Allgemein")) {
                kategorien.add(0, "Allgemein")
                kategorienSpeichern()
            }
        }
    }

    private fun kategorienSpeichern() {
        kategorieEinstellungen.edit()
            .putString(
                "liste",
                kategorien.joinToString("\n")
            )
            .apply()
    }

    private fun neueKategorieDialog() {
        val eingabe = EditText(this).apply {
            hint = "Name der neuen Kategorie"
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(eingabe)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Neue Kategorie erstellen")
            .setView(container)
            .setPositiveButton("Erstellen", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val name = eingabe.text.toString().trim()

                when {
                    name.isBlank() -> {
                        eingabe.error = "Bitte Namen eingeben"
                    }

                    name.contains("\n") ||
                    name.contains("\r") -> {
                        eingabe.error = "Ungültiger Name"
                    }

                    name.equals(
                        "Alle Kategorien",
                        ignoreCase = true
                    ) -> {
                        eingabe.error = "Name ist reserviert"
                    }

                    kategorien.any {
                        it.equals(name, ignoreCase = true)
                    } -> {
                        eingabe.error =
                            "Kategorie existiert bereits"
                    }

                    else -> {
                        kategorien.add(name)
                        kategorienSpeichern()
                        dialog.dismiss()
                        startseiteAnzeigen()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun kategorieOptionen(kategorie: String) {
        AlertDialog.Builder(this)
            .setTitle(kategorie)
            .setItems(
                arrayOf(
                    "Dokumente anzeigen",
                    "Kategorie umbenennen",
                    "Kategorie löschen"
                )
            ) { _, position ->
                when (position) {
                    0 -> dokumentenAnsichtAnzeigen(kategorie)
                    1 -> kategorieUmbenennen(kategorie)
                    2 -> kategorieLoeschenDialog(kategorie)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun kategorieUmbenennen(alt: String) {
        if (alt == "Allgemein") {
            meldung("Allgemein kann nicht umbenannt werden")
            return
        }

        val eingabe = EditText(this).apply {
            setText(alt)
            setSingleLine(true)
            selectAll()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(eingabe)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Kategorie umbenennen")
            .setView(container)
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val neu = eingabe.text.toString().trim()

                when {
                    neu.isBlank() ||
                    neu.contains("\n") ||
                    neu.contains("\r") -> {
                        eingabe.error = "Ungültiger Name"
                    }

                    neu.equals(
                        "Alle Kategorien",
                        ignoreCase = true
                    ) -> {
                        eingabe.error = "Name ist reserviert"
                    }

                    kategorien.any {
                        it != alt &&
                        it.equals(neu, ignoreCase = true)
                    } -> {
                        eingabe.error =
                            "Kategorie existiert bereits"
                    }

                    else -> {
                        val index = kategorien.indexOf(alt)

                        if (index >= 0) {
                            kategorien[index] = neu

                            dokumente.forEach { dokument ->
                                if (
                                    kategorieVon(
                                        dokument.first
                                    ) == alt
                                ) {
                                    kategorieSpeichern(
                                        dokument.first,
                                        neu
                                    )
                                }
                            }

                            kategorienSpeichern()
                            dialog.dismiss()
                            startseiteAnzeigen()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun kategorieLoeschenDialog(
        kategorie: String
    ) {
        if (kategorie == "Allgemein") {
            meldung("Allgemein kann nicht gelöscht werden")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Kategorie löschen?")
            .setMessage(
                "Die Kategorie \"$kategorie\" wird entfernt. " +
                "Alle Dokumente bleiben erhalten und werden " +
                "nach Allgemein verschoben."
            )
            .setPositiveButton("Kategorie löschen") { _, _ ->
                dokumente.forEach { dokument ->
                    if (
                        kategorieVon(dokument.first) ==
                        kategorie
                    ) {
                        kategorieSpeichern(
                            dokument.first,
                            "Allgemein"
                        )
                    }
                }

                kategorien.remove(kategorie)
                kategorienSpeichern()
                startseiteAnzeigen()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun anzahlFuer(kategorie: String): Int {
        return dokumente.count {
            kategorieVon(it.first) == kategorie
        }
    }

    // STARTSEITE

    private fun startseiteAnzeigen() {
        if (!entsperrt) return
        startseiteAktiv = true

        val layout = grundLayout()

        layout.addView(titelText("DokumentenPilot"))

        layout.addView(
            TextView(this).apply {
                text = "Deine Dokumente sicher verwalten"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(dp(4), 0, 0, dp(18))
            }
        )

        layout.addView(
            dunkelblauerButton(
                "＋ PDF SICHER HINZUFÜGEN"
            ) {
                kategorieFuerImportWaehlen()
            }
        )

        layout.addView(
            dunkelblauerButton("📄 UNTERLAGEN-ASSISTENT") {
                AssistentOberflaeche(
                    this,
                    dokumente,
                    ocrTexte,
                    { dateiName -> dokumentOeffnen(dateiName) }
                ).anzeigen()
            }
        )

        layout.addView(
            dunkelblauerButton("📋 UNTERLAGEN AUTOMATISCH ZUSAMMENSTELLEN") {
                val nameEingabe = EditText(this).apply {
                    hint = "z. B. Sparkasse - Kreditantrag"
                    setSingleLine(true)
                }

                AlertDialog.Builder(this)
                    .setTitle("Neue Unterlagenmappe")
                    .setView(nameEingabe)
                    .setPositiveButton("Weiter") { _, _ ->
                        val name = nameEingabe.text.toString().trim()
                        if (name.isBlank()) {
                            meldung("Bitte einen Mappennamen eingeben")
                        } else {
                            val verwaltung = UnterlagenMappen(this)
                            val mappen = verwaltung.laden()
                            val neueMappe = verwaltung.neueMappe(name)
                            mappen.add(neueMappe)
                            verwaltung.speichern(mappen)
                            aktiveAnfrageMappeId = neueMappe.id

                            AlertDialog.Builder(this)
                                .setTitle("Anforderungsschreiben auswählen")
                                .setItems(
                                    arrayOf("Foto machen", "Datei hochladen")
                                ) { _, auswahl ->
                                    when (auswahl) {
                                        0 -> scannerStarten()
                                        1 -> kategorieFuerImportWaehlen()
                                    }
                                }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        )

        layout.addView(
            dunkelblauerButton("📁 MEINE UNTERLAGENMAPPEN") {
                MappenOberflaeche(
                    this,
                    dokumente,
                ocrTexte,
                    { dateiName -> dokumentOeffnen(dateiName) },
                    kategorien,
                    { dateiName -> kategorieVon(dateiName) },
                    { dateiNamen -> mehrereDokumenteTeilen(dateiNamen) }
                ).anzeigen()
            }
        )

        layout.addView(
            TextView(this).apply {
                text = "Meine Kategorien"
                textSize = 21f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(20), 0, dp(12))
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val inhalt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(35))
        }

        val symbole = mapOf(
            "Arbeit" to "💼",
            "Bank & Kredite" to "🏦",
            "Wohnen" to "🏠",
            "Versicherungen" to "🛡️",
            "Allgemein" to "📁",
            "Familie & Behörden" to "🏛️"
        )

        val eintraege = kategorien.map {
            (symbole[it] ?: "📂") to it
        }

        eintraege.chunked(2).forEach { paar ->
            val zeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            paar.forEach { eintrag ->
                val kategorie = eintrag.second

                val karte = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8),
                        dp(14),
                        dp(8),
                        dp(14)
                    )
                    background = hintergrund(
                        Color.WHITE,
                        16f
                    )
                    elevation = dp(3).toFloat()
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        dokumentenAnsichtAnzeigen(kategorie)
                    }

                    setOnLongClickListener {
                        kategorieOptionen(kategorie)
                        true
                    }
                }

                karte.addView(
                    TextView(this).apply {
                        text = eintrag.first
                        textSize = 30f
                        gravity = Gravity.CENTER
                    }
                )

                karte.addView(
                    TextView(this).apply {
                        text = kategorie
                        textSize = 14f
                        setTextColor(dunkelblau)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(
                            0,
                            dp(8),
                            0,
                            dp(5)
                        )
                    }
                )

                karte.addView(
                    TextView(this).apply {
                        val anzahl = anzahlFuer(kategorie)
                        text = if (anzahl == 1) {
                            "1 Dokument"
                        } else {
                            "$anzahl Dokumente"
                        }
                        textSize = 12f
                        setTextColor(Color.DKGRAY)
                        gravity = Gravity.CENTER
                    }
                )

                zeile.addView(
                    karte,
                    LinearLayout.LayoutParams(
                        0,
                        dp(135),
                        1f
                    ).apply {
                        setMargins(
                            dp(4),
                            dp(4),
                            dp(8),
                            dp(10)
                        )
                    }
                )
            }

            inhalt.addView(zeile)
        }

        inhalt.addView(
            dunkelblauerButton(
                "＋ NEUE KATEGORIE"
            ) {
                neueKategorieDialog()
            }
        )

        val scanKarte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(12),
                dp(15),
                dp(12),
                dp(15)
            )
            background = hintergrund(
                Color.WHITE,
                16f
            )
            elevation = dp(3).toFloat()
            setOnClickListener {
                scannerStarten()
            }
        }

        scanKarte.addView(
            TextView(this).apply {
                text = "📷 Dokument scannen"
                textSize = 18f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        )

        scanKarte.addView(
            TextView(this).apply {
                text = "Mehrere Seiten als eine PDF"
                textSize = 12f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            }
        )

        inhalt.addView(
            scanKarte,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(95)
            ).apply {
                setMargins(
                    dp(4),
                    dp(14),
                    dp(8),
                    dp(12)
                )
            }
        )

        inhalt.addView(
            dunkelblauerButton(
                "ALLE DOKUMENTE ANZEIGEN"
            ) {
                dokumentenAnsichtAnzeigen(
                    "Alle Kategorien"
                )
            }
        )

        scroll.addView(inhalt)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)
    }

    // SCANNER

    private fun scannerStarten() {
        val optionen =
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(30)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(
                    GmsDocumentScannerOptions.SCANNER_MODE_FULL
                )
                .build()

        val scanner =
            GmsDocumentScanning.getClient(optionen)

        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                try {
                    scannerGestartet = true
                    scannerStartZeit = android.os.SystemClock.elapsedRealtime()
                    startIntentSenderForResult(
                        intentSender,
                        SCAN_AUSWAHL,
                        null,
                        0,
                        0,
                        0
                    )
                } catch (
                    e: IntentSender.SendIntentException
                ) {
                    scannerGestartet = false
                    scannerStartZeit = 0L
                    meldung(
                        "Scanner konnte nicht gestartet werden"
                    )
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Scanner nicht verfügbar. " +
                    "Bitte Google Play-Dienste prüfen.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun scanSpeichernDialog(
        pdfDaten: ByteArray
    ) {
        val eingabe = EditText(this).apply {
            hint = "Dokumentname"
            setText("Scan.pdf")
            setSingleLine(true)
            selectAll()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(eingabe)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Scan speichern")
            .setMessage(
                "Gib einen Namen für dein " +
                "gescanntes Dokument ein."
            )
            .setView(container)
            .setPositiveButton("Weiter", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val eingegebenerName =
                    eingabe.text.toString().trim()

                if (
                    eingegebenerName.isBlank() ||
                    eingegebenerName.contains("|") ||
                    eingegebenerName.contains("\n") ||
                    eingegebenerName.contains("\r")
                ) {
                    eingabe.error =
                        "Bitte gültigen Dokumentnamen eingeben"
                    return@setOnClickListener
                }

                val dateiname =
                    if (
                        eingegebenerName.endsWith(
                            ".pdf",
                            ignoreCase = true
                        )
                    ) {
                        eingegebenerName
                    } else {
                        "$eingegebenerName.pdf"
                    }

                dialog.dismiss()

                AlertDialog.Builder(this)
                    .setTitle("Kategorie auswählen")
                    .setItems(
                        kategorien.toTypedArray()
                    ) { _, position ->
                        try {
                            val dokumentId = dokumentVerschluesseln(
                                dateiname,
                                pdfDaten,
                                kategorien[position]
                            )

                            aktiveAnfrageMappeId?.let { mappeId ->
                                val verwaltung = UnterlagenMappen(this)
                                val mappen = verwaltung.laden()
                                val mappe = mappen.find { it.id == mappeId }
                                if (mappe != null) {
                                    mappe.dokumentIds.add(dokumentId)
                                    verwaltung.speichern(mappen)
                                    aktiveAnfrageMappeId = null
                                }
                            }

                            dokumenteLaden()

                            if (startseiteAktiv) {
                                startseiteAnzeigen()
                            } else {
                                listeAktualisieren()
                            }

                            meldung("Scan sicher gespeichert")
                        } catch (e: Exception) {
                            meldung(
                                "Scan konnte nicht gespeichert werden"
                            )
                        }
                    }
                    .setNegativeButton(
                        "Abbrechen",
                        null
                    )
                    .show()
            }
        }

        dialog.show()
    }

    // DOKUMENTENANSICHT

    private fun dokumentenAnsichtAnzeigen(
        kategorie: String
    ) {
        if (!entsperrt) return
        startseiteAktiv = false
        aktiveKategorie = kategorie

        val layout = grundLayout()

        layout.addView(
            dunkelblauerButton(
                "← ZUR STARTSEITE"
            ) {
                startseiteAnzeigen()
            }
        )

        layout.addView(
            titelText(
                if (kategorie == "Alle Kategorien") {
                    "Alle Dokumente"
                } else {
                    kategorie
                }
            )
        )

        layout.addView(
            dunkelblauerButton(
                "＋ PDF HINZUFÜGEN"
            ) {
                kategorieFuerImportWaehlen()
            }
        )

        // NEU: OCR starten

        ocrButton = dunkelblauerButton(
            "🔍 PDF-TEXTE ERKENNEN"
        ) {
            ocrFuerAlleStarten()
        }

        layout.addView(ocrButton)

        ocrAnzeige = TextView(this).apply {
            text = "OCR-Suche wird vorbereitet ..."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(dp(4), dp(6), 0, dp(8))
        }

        layout.addView(ocrAnzeige)

        layout.addView(
            TextView(this).apply {
                text = "Dokumente durchsuchen"
                textSize = 16f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(
                    dp(4),
                    dp(12),
                    0,
                    dp(6)
                )
            }
        )

        suche = EditText(this).apply {
            hint = "Dateiname oder Text im PDF ..."
            textSize = 15f
            setSingleLine(true)
            setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
            )
            background = hintergrund(
                Color.WHITE,
                14f
            )

            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        listeAktualisieren()
                    }

                    override fun afterTextChanged(
                        s: Editable?
                    ) {}
                }
            )
        }

        layout.addView(suche)

        anzahlAnzeige = TextView(this).apply {
            textSize = 18f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(
                dp(4),
                dp(18),
                0,
                dp(12)
            )
        }

        layout.addView(anzahlAnzeige)

        dokumentenListe = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(3),
                dp(3),
                dp(3),
                dp(10)
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(dokumentenListe)
        }

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        ocrStatusAktualisieren()
        listeAktualisieren()
        ocrIndexLaden()
    }

    private fun kategorieFuerImportWaehlen() {
        AlertDialog.Builder(this)
            .setTitle("Kategorie auswählen")
            .setItems(
                kategorien.toTypedArray()
            ) { _, position ->
                importKategorie = kategorien[position]

                val intent = Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                ).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png"))
                }

                try {
                    startActivityForResult(
                        intent,
                        PDF_AUSWAHL
                    )
                } catch (e: ActivityNotFoundException) {
                    meldung("Keine Dateiauswahl verfügbar")
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun kategorieVon(
        dateiName: String
    ): String {
        return einstellungen.getString(
            dateiName,
            "Allgemein"
        ) ?: "Allgemein"
    }

    private fun kategorieSpeichern(
        dateiName: String,
        kategorie: String
    ) {
        einstellungen.edit()
            .putString(dateiName, kategorie)
            .apply()
    }

    private fun kategorieAendern(
        dateiName: String
    ) {
        val aktuell = kategorieVon(dateiName)

        val position =
            kategorien.indexOf(aktuell)
                .coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Dokument verschieben")
            .setSingleChoiceItems(
                kategorien.toTypedArray(),
                position
            ) { dialog, auswahl ->
                kategorieSpeichern(
                    dateiName,
                    kategorien[auswahl]
                )
                dialog.dismiss()
                listeAktualisieren()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // VERSCHLÜSSELUNG
    // Schlüsselname und Dateiformat bleiben unverändert.

    private fun schluesselHolen(): SecretKey {
        val keyStore = KeyStore.getInstance(
            "AndroidKeyStore"
        )

        keyStore.load(null)

        val vorhanden = keyStore.getKey(
            schluesselName,
            null
        ) as? SecretKey

        if (vorhanden != null) {
            return vorhanden
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val parameter = KeyGenParameterSpec.Builder(
            schluesselName,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(
                KeyProperties.BLOCK_MODE_GCM
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .build()

        generator.init(parameter)

        return generator.generateKey()
    }

    private fun datenVerschluesseln(
        daten: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            schluesselHolen()
        )

        return byteArrayOf(cipher.iv.size.toByte()) +
            cipher.iv +
            cipher.doFinal(daten)
    }

    private fun datenEntschluesseln(
        daten: ByteArray
    ): ByteArray {
        require(daten.isNotEmpty())

        val ivLaenge = daten[0].toInt() and 0xFF

        require(ivLaenge == 12)
        require(daten.size > ivLaenge + 1)

        val iv = daten.copyOfRange(
            1,
            1 + ivLaenge
        )

        val verschluesselt = daten.copyOfRange(
            1 + ivLaenge,
            daten.size
        )

        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.DECRYPT_MODE,
            schluesselHolen(),
            GCMParameterSpec(128, iv)
        )

        return cipher.doFinal(verschluesselt)
    }

    private fun dokumentVerschluesseln(
        dateiname: String,
        daten: ByteArray,
        kategorie: String
    ): String {
        val ordner = File(
            filesDir,
            "dokumente"
        )

        ordner.mkdirs()

        val datei = File(
            ordner,
            "${UUID.randomUUID()}.dp"
        )

        datei.writeBytes(
            datenVerschluesseln(daten)
        )

        File(
            filesDir,
            "dokumentnamen.txt"
        ).appendText(
            "${datei.name}|$dateiname\n"
        )

        kategorieSpeichern(
            datei.name,
            kategorie
        )
        return datei.name
    }


    private fun bildZuPdf(daten: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(
            daten, 0, daten.size
        ) ?: throw IllegalArgumentException("Bild nicht lesbar")

        val maxSeite = 1600f
        val faktor = minOf(
            maxSeite / bitmap.width,
            maxSeite / bitmap.height,
            1f
        )
        val breite = (bitmap.width * faktor).toInt().coerceAtLeast(1)
        val hoehe = (bitmap.height * faktor).toInt().coerceAtLeast(1)

        val pdf = PdfDocument()
        try {
            val seite = pdf.startPage(
                PdfDocument.PageInfo.Builder(breite, hoehe, 1).create()
            )
            val ziel = android.graphics.Rect(
                0, 0, breite, hoehe
            )
            seite.canvas.drawBitmap(bitmap, null, ziel, null)
            pdf.finishPage(seite)

            val ausgabe = ByteArrayOutputStream()
            pdf.writeTo(ausgabe)
            return ausgabe.toByteArray()
        } finally {
            pdf.close()
            bitmap.recycle()
        }
    }

    private fun dokumenteLaden() {
        dokumente.clear()

        val namenDatei = File(
            filesDir,
            "dokumentnamen.txt"
        )

        if (!namenDatei.exists()) {
            return
        }

        namenDatei.forEachLine { zeile ->
            val teile = zeile.split(
                "|",
                limit = 2
            )

            if (teile.size == 2) {
                dokumente.add(
                    teile[0] to teile[1]
                )
            }
        }
    }

    private fun dokumentEntschluesseln(
        dateiName: String
    ): ByteArray {
        val datei = File(
            File(filesDir, "dokumente"),
            dateiName
        )

        return datenEntschluesseln(
            datei.readBytes()
        )
    }

    // OCR-SUCHE
    // Die OCR-Dateien werden mit demselben
    // AndroidKeyStore-Schlüssel verschlüsselt.
    // Bestehende PDF-Dateien bleiben unverändert.

    private fun ocrOrdner(): File {
        return File(filesDir, "ocr_index").apply {
            mkdirs()
        }
    }

    private fun ocrDatei(
        dateiName: String
    ): File {
        return File(
            ocrOrdner(),
            "$dateiName.ocr"
        )
    }

    private fun ocrStatusAktualisieren() {
        if (
            startseiteAktiv ||
            !::ocrAnzeige.isInitialized ||
            !::ocrButton.isInitialized
        ) {
            return
        }

        val anzahl = dokumente.count {
            ocrDatei(it.first).exists()
        }

        ocrAnzeige.text = if (ocrLaeuft) {
            "Texterkennung läuft im Hintergrund ..."
        } else {
            "$anzahl von ${dokumente.size} Dokumenten für die Textsuche erfasst"
        }

        ocrButton.isEnabled = !ocrLaeuft
    }

    private fun ocrIndexLaden() {
        val generation = ++ocrGeneration

        val namen = dokumente.map {
            it.first
        }

        Thread {
            val geladeneTexte =
                mutableMapOf<String, String>()

            namen.forEach { name ->
                val datei = ocrDatei(name)

                if (datei.exists()) {
                    try {
                        geladeneTexte[name] =
                            String(
                                datenEntschluesseln(
                                    datei.readBytes()
                                ),
                                Charsets.UTF_8
                            )
                    } catch (e: Exception) {
                        // Ein fehlerhafter OCR-Index
                        // darf die PDFs nicht beeinflussen.
                    }
                }
            }

            runOnUiThread {
                if (entsperrt && generation == ocrGeneration) {
                    ocrTexte.clear()
                    ocrTexte.putAll(geladeneTexte)

                    if (!startseiteAktiv) {
                        listeAktualisieren()
                        ocrStatusAktualisieren()
                    }
                }
            }
        }.start()
    }

    private fun ocrFuerAlleStarten() {
        if (ocrLaeuft) {
            meldung("Texterkennung läuft bereits")
            return
        }

        val nochNichtErfasst = dokumente
            .map { it.first }
            .filter {
                !ocrDatei(it).exists()
            }

        if (nochNichtErfasst.isEmpty()) {
            meldung(
                "Alle Dokumente wurden bereits erfasst"
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle("PDF-Texte erkennen?")
            .setMessage(
                "${nochNichtErfasst.size} Dokument(e) " +
                "werden auf diesem Gerät verarbeitet.\n\n" +
                "Das kann bei vielen PDF-Seiten " +
                "einige Minuten dauern."
            )
            .setPositiveButton("Starten") { _, _ ->
                ocrLaeuft = true
                ocrStatusAktualisieren()

                Thread {
                    var erfolgreich = 0
                    var fehler = 0

                    nochNichtErfasst.forEachIndexed {
                        index, name ->

                        try {
                            val pdfDaten =
                                dokumentEntschluesseln(name)

                            // OcrHilfe.kt führt die
                            // Texterkennung aus.
                            val text = OcrHilfe.textAusPdf(
                                applicationContext,
                                pdfDaten
                            )

                            // Nur verschlüsselt speichern
                            ocrDatei(name).writeBytes(
                                datenVerschluesseln(
                                    text.toByteArray(
                                        Charsets.UTF_8
                                    )
                                )
                            )

                            erfolgreich++

                            runOnUiThread {
                                if (entsperrt) ocrTexte[name] = text

                                if (entsperrt && !startseiteAktiv) {
                                    listeAktualisieren()
                                }
                            }

                        } catch (e: Exception) {
                            fehler++
                        }

                        runOnUiThread {
                            if (
                                entsperrt && !startseiteAktiv &&
                                ::ocrAnzeige.isInitialized
                            ) {
                                ocrAnzeige.text =
                                    "OCR: ${index + 1} von " +
                                    "${nochNichtErfasst.size} verarbeitet"
                            }
                        }
                    }

                    runOnUiThread {
                        ocrLaeuft = false
                        ocrStatusAktualisieren()

                        if (!entsperrt) return@runOnUiThread
                        AlertDialog.Builder(this)
                            .setTitle(
                                "Texterkennung abgeschlossen"
                            )
                            .setMessage(
                                "$erfolgreich Dokument(e) " +
                                "erfolgreich erfasst.\n" +
                                "$fehler Dokument(e) " +
                                "konnten nicht verarbeitet werden."
                            )
                            .setPositiveButton(
                                "OK",
                                null
                            )
                            .show()
                    }
                }.start()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // DOKUMENT UMBENENNEN

    private fun dokumentUmbenennen(
        dateiName: String
    ) {
        val dokument = dokumente.find {
            it.first == dateiName
        } ?: return

        val eingabe = EditText(this).apply {
            setText(dokument.second)
            setSingleLine(true)
            selectAll()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(eingabe)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Dokument umbenennen")
            .setView(container)
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val eingabeName =
                    eingabe.text.toString().trim()

                if (
                    eingabeName.isBlank() ||
                    eingabeName.contains("|") ||
                    eingabeName.contains("\n") ||
                    eingabeName.contains("\r")
                ) {
                    eingabe.error =
                        "Bitte gültigen Namen eingeben"
                    return@setOnClickListener
                }

                val neuerName =
                    if (
                        eingabeName.endsWith(
                            ".pdf",
                            ignoreCase = true
                        )
                    ) {
                        eingabeName
                    } else {
                        "$eingabeName.pdf"
                    }

                try {
                    val namenDatei = File(
                        filesDir,
                        "dokumentnamen.txt"
                    )

                    val neueListe = dokumente.map {
                        if (it.first == dateiName) {
                            it.first to neuerName
                        } else {
                            it
                        }
                    }

                    val tempDatei = File(
                        filesDir,
                        "dokumentnamen_neu.tmp"
                    )

                    tempDatei.writeText(
                        neueListe.joinToString("\n") {
                            "${it.first}|${it.second}"
                        } + if (
                            neueListe.isNotEmpty()
                        ) "\n" else ""
                    )

                    if (!tempDatei.renameTo(namenDatei)) {
                        throw IllegalStateException(
                            "Namensliste konnte nicht gespeichert werden"
                        )
                    }

                    dokumenteLaden()
                    dialog.dismiss()
                    listeAktualisieren()
                    meldung("Dokument umbenannt")

                } catch (e: Exception) {
                    meldung(
                        "Umbenennen fehlgeschlagen"
                    )
                }
            }
        }

        dialog.show()
    }

    // PDF-VORSCHAU UND TEILEN

    private fun vorschauDateiErstellen(
        dateiName: String
    ): File {
        val entschluesselt =
            dokumentEntschluesseln(dateiName)

        val vorschauOrdner = File(
            cacheDir,
            "pdf_vorschau"
        )

        vorschauOrdner.mkdirs()

        vorschauOrdner.listFiles()?.forEach {
            it.delete()
        }

        val pdfDatei = File(
            vorschauOrdner,
            "vorschau.pdf"
        )

        pdfDatei.writeBytes(entschluesselt)

        return pdfDatei
    }


    private fun mehrereDokumenteTeilen(dateiNamen: List<String>) {
        if (dateiNamen.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Unterlagen versenden?")
            .setMessage(
                "Die ausgewählten PDFs werden zum Teilen " +
                "vorübergehend entschlüsselt. Die empfangende " +
                "App erhält unverschlüsselte Kopien.\\n\\n" +
                "Möchtest du fortfahren?"
            )
            .setPositiveButton("Weiter") { _, _ ->
                try {
                    val ordner = File(cacheDir, "pdf_versand")
                    ordner.mkdirs()
                    ordner.listFiles()?.forEach { it.delete() }

                    val uris = ArrayList<android.net.Uri>()

                    dateiNamen.distinct().forEachIndexed { index, name ->
                        val originalName = dokumente.find {
                                    it.first == name
                                }?.second ?: name

                                val sichererName = originalName
                                    .substringAfterLast('/')
                                    .substringAfterLast('\\')
                                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                                    .ifBlank { "Dokument_${index + 1}" }

                                val pdfName = if (
                                    sichererName.endsWith(".pdf", ignoreCase = true)
                                ) {
                                    sichererName
                                } else {
                                    "$sichererName.pdf"
                                }

                                val datei = File(ordner, "${index + 1}_$pdfName")
                        datei.writeBytes(dokumentEntschluesseln(name))

                        uris.add(
                            FileProvider.getUriForFile(
                                this,
                                "de.dokumentenpilot.app.fileprovider",
                                datei
                            )
                        )
                    }

                    val intent = Intent(
                        if (uris.size == 1)
                            Intent.ACTION_SEND
                        else
                            Intent.ACTION_SEND_MULTIPLE
                    ).apply {
                        type = "application/pdf"
                        if (uris.size == 1) {
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                        } else {
                            putParcelableArrayListExtra(
                                Intent.EXTRA_STREAM,
                                uris
                            )
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        val clips = android.content.ClipData.newUri(
                            contentResolver,
                            (if (uris.size == 1) "Dokument" else "${uris.size} Dokumente"),
                                        uris[0]
                        )
                        uris.drop(1).forEach { clips.addItem(android.content.ClipData.Item(it)) }
                        clipData = clips
                    }

                    startActivity(
                        Intent.createChooser(intent, "Unterlagen teilen mit")
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Unterlagen konnten nicht geteilt werden",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun dokumentTeilen(
        dateiName: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("PDF teilen?")
            .setMessage(
                "Beim Teilen wird eine entschlüsselte " +
                "Kopie der PDF an die ausgewählte App " +
                "weitergegeben. Dort ist sie nicht mehr " +
                "durch DokumentenPilot geschützt.\n\n" +
                "Möchtest du fortfahren?"
            )
            .setPositiveButton("Weiter") { _, _ ->
                try {
                    val pdfDatei =
                        vorschauDateiErstellen(dateiName)

                    val uri = FileProvider.getUriForFile(
                        this,
                        "de.dokumentenpilot.app.fileprovider",
                        pdfDatei
                    )

                    val intent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "application/pdf"
                        putExtra(
                            Intent.EXTRA_STREAM,
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        clipData =
                            android.content.ClipData.newUri(
                                contentResolver,
                                "PDF",
                                uri
                            )
                    }

                    startActivity(
                        Intent.createChooser(
                            intent,
                            "PDF teilen mit"
                        )
                    )

                } catch (e: Exception) {
                    meldung(
                        "PDF konnte nicht geteilt werden"
                    )
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // DOKUMENTENLISTE MIT OCR-SUCHE

    private fun listeAktualisieren() {
        if (
            startseiteAktiv ||
            !::dokumentenListe.isInitialized ||
            !::anzahlAnzeige.isInitialized ||
            !::suche.isInitialized
        ) {
            return
        }

        dokumentenListe.removeAllViews()

        val suchtext = suche.text
            .toString()
            .trim()

        val sichtbareDokumente =
            dokumente.filter { dokument ->

                val namePasst =
                    dokument.second.contains(
                        suchtext,
                        ignoreCase = true
                    )

                // NEU: Auch den erkannten PDF-Text prüfen
                val textPasst =
                    ocrTexte[dokument.first]
                        ?.contains(
                            suchtext,
                            ignoreCase = true
                        ) == true

                val kategoriePasst =
                    aktiveKategorie == "Alle Kategorien" ||
                    kategorieVon(dokument.first) ==
                        aktiveKategorie

                kategoriePasst &&
                    (namePasst || textPasst)
            }

        anzahlAnzeige.text =
            "Dokumente: ${sichtbareDokumente.size}"

        if (sichtbareDokumente.isEmpty()) {
            dokumentenListe.addView(
                TextView(this).apply {
                    text = "Keine Dokumente gefunden"
                    textSize = 16f
                    setTextColor(Color.DKGRAY)
                    setPadding(
                        dp(12),
                        dp(25),
                        0,
                        dp(20)
                    )
                }
            )
            return
        }

        sichtbareDokumente.forEach { dokument ->
            val karte = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(12)
                )
                background = hintergrund(
                    Color.WHITE,
                    16f
                )
                elevation = dp(3).toFloat()
            }

            karte.addView(
                TextView(this).apply {
                    text = "📄 ${dokument.second}"
                    textSize = 17f
                    setTextColor(dunkelblau)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, dp(8))

                    setOnClickListener {
                        dokumentOeffnen(
                            dokument.first
                        )
                    }
                }
            )

            karte.addView(
                TextView(this).apply {
                    text =
                        "📁 ${kategorieVon(dokument.first)}"
                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, 0, 0, dp(8))
                }
            )

            // Kennzeichnung erkannter Texte
            if (ocrTexte.containsKey(dokument.first)) {
                karte.addView(
                    TextView(this).apply {
                        text = "🔍 Textsuche verfügbar"
                        textSize = 11f
                        setTextColor(
                            Color.rgb(35, 110, 65)
                        )
                        setPadding(0, 0, 0, dp(5))
                    }
                )
            }

            val ersteZeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val zweiteZeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            fun knopfParameter() =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

            ersteZeile.addView(
                dunkelblauerButton("Öffnen") {
                    dokumentOeffnen(dokument.first)
                },
                knopfParameter()
            )

            ersteZeile.addView(
                Button(this).apply {
                    text = "Umbenennen"
                    textSize = 11f
                    setOnClickListener {
                        dokumentUmbenennen(
                            dokument.first
                        )
                    }
                },
                knopfParameter()
            )

            ersteZeile.addView(
                Button(this).apply {
                    text = "Teilen"
                    textSize = 12f
                    setOnClickListener {
                        dokumentTeilen(
                            dokument.first
                        )
                    }
                },
                knopfParameter()
            )

            zweiteZeile.addView(
                Button(this).apply {
                    text = "Ordner"
                    textSize = 12f
                    setOnClickListener {
                        kategorieAendern(
                            dokument.first
                        )
                    }
                },
                knopfParameter()
            )

            zweiteZeile.addView(
                Button(this).apply {
                    text = "Löschen"
                    textSize = 12f

                    setOnClickListener {
                        AlertDialog.Builder(
                            this@MainActivity
                        )
                            .setTitle(
                                "Dokument löschen?"
                            )
                            .setMessage(
                                "Möchtest du dieses " +
                                "Dokument wirklich löschen?"
                            )
                            .setPositiveButton(
                                "Löschen"
                            ) { _, _ ->
                                dokumentLoeschen(
                                    dokument.first
                                )
                            }
                            .setNegativeButton(
                                "Abbrechen",
                                null
                            )
                            .show()
                    }
                },
                knopfParameter()
            )

            karte.addView(ersteZeile)
            karte.addView(zweiteZeile)

            dokumentenListe.addView(
                karte,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        0,
                        dp(4),
                        0,
                        dp(14)
                    )
                }
            )
        }
    }

    private fun dokumentOeffnen(
        dateiName: String
    ) {
        try {
            val pdfDatei =
                vorschauDateiErstellen(dateiName)

            val uri = FileProvider.getUriForFile(
                this,
                "de.dokumentenpilot.app.fileprovider",
                pdfDatei
            )

            val intent = Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(
                    uri,
                    "application/pdf"
                )
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            pdfVorschauGestartet = true
            startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Keine PDF-App zum Öffnen gefunden",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDF konnte nicht geöffnet werden",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // IMPORT UND SCAN-ERGEBNIS

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == SCAN_AUSWAHL) {
            val dauer = if (scannerStartZeit > 0L) {
                android.os.SystemClock.elapsedRealtime() - scannerStartZeit
            } else {
                0L
            }
            scannerGestartet = false
            scannerStartZeit = 0L

            if (dauer > 120000L) {
                entsperrt = false
                ++ocrGeneration
                ocrTexte.clear()
                sperrbildschirmAnzeigen()
            }
        }

        if (requestCode == AppSperre.PIN_ANFRAGE) {
            pinAnmeldungLaeuft = false
            anmeldungLaeuft = false
            if (resultCode == RESULT_OK) {
                entsperrt = true
                if (startseiteAktiv) startseiteAnzeigen()
                else dokumentenAnsichtAnzeigen(aktiveKategorie)
                ausstehendesErgebnis?.let { ergebnis ->
                    ausstehendesErgebnis = null
                    ergebnisVerarbeiten(ergebnis.first, ergebnis.second, ergebnis.third)
                }
            } else {
                entsperrt = false
                sperrbildschirmAnzeigen()
            }
            return
        }

        if (!entsperrt) {
            if (resultCode == RESULT_OK) {
                ausstehendesErgebnis = Triple(requestCode, resultCode, data)
            }
            return
        }
        ergebnisVerarbeiten(requestCode, resultCode, data)
    }

    private fun ergebnisVerarbeiten(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (!entsperrt || resultCode != RESULT_OK) return

        if (requestCode == SCAN_AUSWAHL) {
            try {
                val ergebnis =
                    GmsDocumentScanningResult
                        .fromActivityResultIntent(data)

                val pdf = ergebnis?.pdf

                if (pdf == null) {
                    meldung("Keine PDF erhalten")
                    return
                }

                val daten = contentResolver
                    .openInputStream(pdf.uri)
                    ?.use { it.readBytes() }
                    ?: throw IllegalStateException(
                        "Scan konnte nicht gelesen werden"
                    )

                scanSpeichernDialog(daten)

            } catch (e: Exception) {
                meldung(
                    "Scan konnte nicht verarbeitet werden"
                )
            }

            return
        }

        if (requestCode != PDF_AUSWAHL) {
            return
        }

        val uri = data?.data ?: return
        var dateiname = "Dokument.pdf"

        try {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (
                    index >= 0 &&
                    cursor.moveToFirst()
                ) {
                    dateiname =
                        cursor.getString(index)
                            ?: "Dokument.pdf"
                }
            }

            val daten = contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }
                ?: throw IllegalStateException(
                    "Datei konnte nicht gelesen werden"
                )

            // Keine Zeilenumbrüche in der Namensliste
            dateiname = dateiname
                .replace("\n", " ")
                .replace("\r", " ")

            val bildImport = dateiname.endsWith(".jpg", true) ||
                dateiname.endsWith(".jpeg", true) ||
                dateiname.endsWith(".png", true)

            val speicherDaten = if (bildImport) bildZuPdf(daten) else daten
            val speicherName = if (bildImport) {
                dateiname.substringBeforeLast(".") + ".pdf"
            } else dateiname

            val dokumentId = dokumentVerschluesseln(
                speicherName,
                speicherDaten,
                importKategorie
            )

            val mappeId = aktiveAnfrageMappeId
            val anfrageMappeFuerOcr = mappeId
            if (mappeId != null) {
                val verwaltung = UnterlagenMappen(this)
                val mappen = verwaltung.laden()
                val mappe = mappen.find { it.id == mappeId }

                if (mappe != null) {
                    mappe.dokumentIds.add(dokumentId)
                    verwaltung.speichern(mappen)
                }
                aktiveAnfrageMappeId = null
            }

            dokumenteLaden()

            if (startseiteAktiv) {
                startseiteAnzeigen()
            } else {
                listeAktualisieren()
                ocrStatusAktualisieren()
            }

            meldung("Dokument gespeichert")

            Thread {
                try {
                    val text = OcrHilfe.textAusPdf(
                        applicationContext,
                        speicherDaten
                    )

                    ocrDatei(dokumentId).writeBytes(
                        datenVerschluesseln(
                            text.toByteArray(Charsets.UTF_8)
                        )
                    )

                    runOnUiThread {
                        if (entsperrt) {
                            ocrTexte[dokumentId] = text
                        val angefordert =
                            UnterlagenErkennung.angeforderteUnterlagen(text)

                if (anfrageMappeFuerOcr != null && angefordert.isNotEmpty()) {
                    val verwaltung = UnterlagenMappen(this)
                    val mappen = verwaltung.laden()
                    val mappe = mappen.find { it.id == anfrageMappeFuerOcr }
                    if (mappe != null) {
                        val erkannteAnforderungen =
                            UnterlagenErkennung.anforderungenErkennen(text)

                        erkannteAnforderungen.forEach { anforderung ->
                            mappe.anforderungen.removeAll {
                                it.dokumentenart == anforderung.dokumentenart
                            }
                            mappe.anforderungen.add(anforderung)
                        }

                        angefordert.forEach { unterlage ->
                            if (!mappe.angeforderteUnterlagen.contains(unterlage)) {
                                mappe.angeforderteUnterlagen.add(unterlage)
                            }
                        }
                        verwaltung.speichern(mappen)
                    }
                }

                        if (angefordert.isNotEmpty()) {
                            AlertDialog.Builder(this)
                                .setTitle("Angeforderte Unterlagen erkannt")
                                .setMessage(angefordert.joinToString("\n") {
                                    "• $it"
                                })
                                .setPositiveButton("OK", null)
                                .show()
                        }
                            if (!startseiteAktiv) {
                                listeAktualisieren()
                            }
                            ocrStatusAktualisieren()
                            meldung("Texterkennung abgeschlossen")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (entsperrt) {
                            meldung("Dokument gespeichert, Texterkennung fehlgeschlagen")
                        }
                    }
                }
            }.start()

        } catch (e: Exception) {
            meldung("Speichern fehlgeschlagen")
        }
    }

    // DOKUMENT LÖSCHEN

    private fun dokumentLoeschen(
        dateiName: String
    ) {
        if (ocrLaeuft) {
            meldung(
                "Bitte warten, bis die Texterkennung fertig ist"
            )
            return
        }

        try {
            val datei = File(
                File(filesDir, "dokumente"),
                dateiName
            )

            if (
                !datei.exists() ||
                !datei.delete()
            ) {
                meldung(
                    "Datei konnte nicht gelöscht werden"
                )
                return
            }

            val namenDatei = File(
                filesDir,
                "dokumentnamen.txt"
            )

            val uebrige = dokumente.filter {
                it.first != dateiName
            }

            namenDatei.writeText(
                uebrige.joinToString("\n") {
                    "${it.first}|${it.second}"
                } + if (
                    uebrige.isNotEmpty()
                ) "\n" else ""
            )

            // Zugehörigen OCR-Index ebenfalls entfernen
            ocrDatei(dateiName).delete()
            ocrTexte.remove(dateiName)

            einstellungen.edit()
                .remove(dateiName)
                .apply()

            dokumenteLaden()
            listeAktualisieren()
            ocrStatusAktualisieren()

            meldung("Dokument gelöscht")

        } catch (e: Exception) {
            meldung("Löschen fehlgeschlagen")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!entsperrt) {
            finish()
            return
        }
        if (!startseiteAktiv) {
            startseiteAnzeigen()
        } else {
            super.onBackPressed()
        }
    }
}
