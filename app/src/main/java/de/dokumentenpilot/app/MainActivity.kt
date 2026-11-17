
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

    private var importKategorie = "Allgemein"
    private var aktiveKategorie = "Alle Kategorien"
    private var startseiteAktiv = true

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
        kategorienLaden()
        dokumenteLaden()
        startseiteAnzeigen()
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

    // Kategorien

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

    // Startseite

    private fun startseiteAnzeigen() {
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

        // Mehrseitiger Scanner

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

    // Scanner

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
                            dokumentVerschluesseln(
                                dateiname,
                                pdfDaten,
                                kategorien[position]
                            )

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

    // Dokumentenansicht

    private fun dokumentenAnsichtAnzeigen(
        kategorie: String
    ) {
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

        layout.addView(
            TextView(this).apply {
                text = "Dokumente durchsuchen"
                textSize = 16f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(
                    dp(4),
                    dp(18),
                    0,
                    dp(6)
                )
            }
        )

        suche = EditText(this).apply {
            hint = "Nach Dokumentnamen suchen ..."
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
        listeAktualisieren()
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
                    type = "application/pdf"
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

    // AES-GCM-Verschlüsselung bleibt erhalten

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

    private fun dokumentVerschluesseln(
        dateiname: String,
        daten: ByteArray,
        kategorie: String
    ) {
        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            schluesselHolen()
        )

        val iv = cipher.iv
        val verschluesselt = cipher.doFinal(daten)

        val ordner = File(
            filesDir,
            "dokumente"
        )

        ordner.mkdirs()

        val datei = File(
            ordner,
            "${UUID.randomUUID()}.dp"
        )

        datei.outputStream().use { ausgabe ->
            ausgabe.write(iv.size)
            ausgabe.write(iv)
            ausgabe.write(verschluesselt)
        }

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

    // NEU: Namen sicher ändern
    // Nur die Namensliste wird geändert.
    // Die verschlüsselte .dp-Datei bleibt unverändert.

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

    // Verschlüsselte PDF entschlüsseln
    // Für Vorschau und bewusstes Teilen

    private fun dokumentEntschluesseln(
        dateiName: String
    ): ByteArray {
        val verschluesselteDatei = File(
            File(filesDir, "dokumente"),
            dateiName
        )

        val daten = verschluesselteDatei.readBytes()

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

    // NEU: Dokument teilen

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

    // Dokumentenliste

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
            dokumente.filter {
                val namePasst =
                    it.second.contains(
                        suchtext,
                        ignoreCase = true
                    )

                val kategoriePasst =
                    aktiveKategorie == "Alle Kategorien" ||
                    kategorieVon(it.first) ==
                        aktiveKategorie

                namePasst && kategoriePasst
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

            // Erste Reihe: Öffnen, Umbenennen, Teilen

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

            // Zweite Reihe: Ordner, Löschen

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

        if (resultCode != RESULT_OK) {
            return
        }

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

            dokumentVerschluesseln(
                dateiname,
                daten,
                importKategorie
            )

            dokumenteLaden()

            if (startseiteAktiv) {
                startseiteAnzeigen()
            } else {
                listeAktualisieren()
            }

            meldung("Dokument gespeichert")

        } catch (e: Exception) {
            meldung("Speichern fehlgeschlagen")
        }
    }

    private fun dokumentLoeschen(
        dateiName: String
    ) {
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

            einstellungen.edit()
                .remove(dateiName)
                .apply()

            dokumenteLaden()
            listeAktualisieren()

            meldung("Dokument gelöscht")

        } catch (e: Exception) {
            meldung("Löschen fehlgeschlagen")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!startseiteAktiv) {
            startseiteAnzeigen()
        } else {
            super.onBackPressed()
        }
    }
}
