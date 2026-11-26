
package de.dokumentenpilot.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import java.util.Locale
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
import android.view.View
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
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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

    private fun kategorieAnzeigename(kategorie: String): String {
        val id = when (kategorie) {
            "Allgemein" -> R.string.category_general
            "Arbeit" -> R.string.category_work
            "Bank & Kredite" -> R.string.category_banking
            "Wohnen" -> R.string.category_housing
            "Versicherungen" -> R.string.category_insurance
            "Familie & Behörden" -> R.string.category_family
            else -> return kategorie
        }
        return getString(id)
    }

    private val kategorien = mutableListOf<String>()

    private val dunklerModus: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val dunkelblau: Int
        get() = if (dunklerModus) Color.rgb(213, 226, 255) else Color.rgb(25, 55, 110)
    private val hellblau: Int
        get() = if (dunklerModus) Color.rgb(20, 27, 39) else Color.rgb(240, 245, 255)
    private val kartenFarbe: Int
        get() = if (dunklerModus) Color.rgb(35, 44, 59) else Color.WHITE
    private val sekundaerTextfarbe: Int
        get() = if (dunklerModus) Color.rgb(198, 207, 222) else Color.DKGRAY
    private val aktionsblau: Int
        get() = if (dunklerModus) Color.rgb(47, 75, 137) else Color.rgb(25, 55, 110)

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
    private var ocrIndexLaedt = false
    private val laufendeImportOcr = mutableSetOf<String>()

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

    override fun attachBaseContext(newBase: Context) {
        val einstellungen = newBase.getSharedPreferences(
            "app_sprache",
            Context.MODE_PRIVATE
        )
        val sprachcode = einstellungen.getString("sprachcode", "auto") ?: "auto"

        if (Build.VERSION.SDK_INT >= 33 || sprachcode == "auto") {
            super.attachBaseContext(newBase)
            return
        }

        val konfiguration = Configuration(newBase.resources.configuration)
        konfiguration.setLocale(Locale.forLanguageTag(sprachcode))
        konfiguration.setLayoutDirection(Locale.forLanguageTag(sprachcode))

        super.attachBaseContext(
            newBase.createConfigurationContext(konfiguration)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread { OcrHilfe.alteTempDateienEntfernen(applicationContext) }.start()
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
            text = getString(R.string.lock_message)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        })
        layout.addView(dunkelblauerButton(getString(R.string.lock_unlock)) {
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
                ocrIndexLaden()
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
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(hellblau)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout() or androidx.core.view.WindowInsetsCompat.Type.ime())
                view.setPadding(dp(16) + bars.left, dp(16) + bars.top, dp(16) + bars.right, dp(14) + bars.bottom)
                insets
            }
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
                ColorStateList.valueOf(aktionsblau)
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
            hint = getString(R.string.ui_text_017)
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
            .setTitle(getString(R.string.ui_text_016))
            .setView(container)
            .setPositiveButton(getString(R.string.ui_text_005), null)
            .setNegativeButton(getString(R.string.ui_text_000), null)
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
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    private fun kategorieUmbenennen(alt: String) {
        if (alt == "Allgemein") {
            meldung(getString(R.string.ui_text_018))
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
            .setTitle(getString(R.string.ui_text_019))
            .setView(container)
            .setPositiveButton(getString(R.string.ui_text_004), null)
            .setNegativeButton(getString(R.string.ui_text_000), null)
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
            meldung(getString(R.string.ui_text_020))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_text_021))
            .setMessage(
                "Die Kategorie \"$kategorie\" wird entfernt. " +
                "Alle Dokumente bleiben erhalten und werden " +
                "nach Allgemein verschoben."
            )
            .setPositiveButton(getString(R.string.ui_text_022)) { _, _ ->
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
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    private fun anzahlFuer(kategorie: String): Int {
        return dokumente.count {
            kategorieVon(it.first) == kategorie
        }
    }

    // STARTSEITE

    private val sprachEinstellungen by lazy {
        getSharedPreferences("app_sprache", MODE_PRIVATE)
    }

    private fun gespeicherteSprache(): String {
        return sprachEinstellungen.getString("sprachcode", "auto") ?: "auto"
    }

    private fun androidSpracheSetzen(sprachcode: String) {
        if (Build.VERSION.SDK_INT < 33) return

        val manager = getSystemService(LocaleManager::class.java)
        val sprachliste = if (sprachcode == "auto") {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(sprachcode)
        }

        manager.setApplicationLocales(sprachliste)
    }

    private fun spracheSpeichern(sprachcode: String) {
        sprachEinstellungen.edit()
            .putString("sprachcode", sprachcode)
            .apply()
    }

    private fun spracheAuswaehlen() {
        if (!entsperrt) return

        val sprachcodes = listOf(
            "auto", "de", "en", "tr", "ar",
            "es", "ru", "pl", "uk", "fr"
        )

        val sprachen = listOf(
            "Automatisch (Gerätesprache)",
            "Deutsch",
            "English",
            "Türkçe",
            "العربية",
            "Español",
            "Русский",
            "Polski",
            "Українська",
            "Français"
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_language))
            .setItems(sprachen.toTypedArray()) { _, position ->
                val sprachcode = sprachcodes[position]
                startseiteAktiv = true
                spracheSpeichern(sprachcode)
                androidSpracheSetzen(sprachcode)
                if (Build.VERSION.SDK_INT < 33) {
                    recreate()
                }
                android.widget.Toast.makeText(
                    this,
                    "Gespeichert: ${sprachen[position]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.settings_cancel), null)
            .show()
    }

    private fun einstellungenAnzeigen() {
        if (!entsperrt) return

        startseiteAktiv = false

        val layout = grundLayout()

        layout.addView(titelText(getString(R.string.settings_title)))

        layout.addView(
            dunkelblauerButton("🌐 " + getString(R.string.settings_language)) {
                spracheAuswaehlen()
            }
        )

        layout.addView(
            dunkelblauerButton("← " + getString(R.string.settings_back)) {
                startseiteAnzeigen()
            }
        )

        setContentView(layout)
    }

    private fun assistentStarten() {
        if (!entsperrt) return
        if (ocrIndexLaedt) {
            meldung(getString(R.string.ui_text_032))
            return
        }
        AssistentOberflaeche(
            this, dokumente, ocrTexte,
            { dokumentId -> dokumentOeffnen(dokumentId) },
            { ids -> mehrereDokumenteTeilen(ids) }
        ).anzeigen()
    }

    private fun mappenStarten() {
        if (!entsperrt) return
        if (ocrIndexLaedt) {
            meldung(getString(R.string.ui_text_032))
            return
        }
        MappenOberflaeche(
            this, dokumente, ocrTexte,
            { dokumentId -> dokumentOeffnen(dokumentId) },
            kategorien, { dokumentId -> kategorieVon(dokumentId) },
            { ids -> mehrereDokumenteTeilen(ids) }
        ).anzeigen()
    }

    private fun funktionsMenueAnzeigen() {
        if (!entsperrt) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.home_menu)
            .setView(scroll).setNegativeButton(R.string.home_cancel, null).create()
        fun action(label: Int, icon: String, target: () -> Unit): Button =
            dunkelblauerButton(icon + "  " + getString(label)) { dialog.dismiss(); target() }
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val toggle = dunkelblauerButton("▸ " + getString(R.string.menu_assistants)) {}
        toggle.setOnClickListener {
            val expanded = group.visibility != View.VISIBLE
            group.visibility = if (expanded) View.VISIBLE else View.GONE
            toggle.text = (if (expanded) "▾ " else "▸ ") + getString(R.string.menu_assistants)
        }
        content.addView(toggle)
        group.addView(action(R.string.home_assistant, "⌕") { assistentStarten() })
        group.addView(action(R.string.home_collect, "▤") { unterlagenZusammenstellenStarten() })
        content.addView(group)
        content.addView(action(R.string.home_folders, "▣") { mappenStarten() })
        content.addView(action(R.string.home_show_all, "▤") { dokumentenAnsichtAnzeigen("Alle Kategorien") })
        content.addView(action(R.string.family_sharing_title, "♧") {
            AlertDialog.Builder(this).setTitle(R.string.family_sharing_title)
                .setMessage(R.string.family_sharing_disabled)
                .setPositiveButton(android.R.string.ok, null).show()
        })
        content.addView(action(R.string.settings_title, "⚙") { einstellungenAnzeigen() })
        dialog.show()
    }

    private fun unterlagenZusammenstellenStarten() {
                val nameEingabe = EditText(this).apply {
                    hint = getString(R.string.home_folder_hint)
                    setSingleLine(true)
                }

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.home_new_folder))
                    .setView(nameEingabe)
                    .setPositiveButton(getString(R.string.home_next)) { _, _ ->
                        val name = nameEingabe.text.toString().trim()
                        if (name.isBlank()) {
                            meldung(getString(R.string.home_folder_required))
                        } else {
                            val verwaltung = UnterlagenMappen(this)
                            val mappen = verwaltung.laden()
                            val neueMappe = verwaltung.neueMappe(name)
                            mappen.add(neueMappe)
                            verwaltung.speichern(mappen)
                            aktiveAnfrageMappeId = neueMappe.id

                            AlertDialog.Builder(this)
                                .setTitle(getString(R.string.home_choose_request))
                                .setItems(
                                    arrayOf(getString(R.string.home_take_photo), getString(R.string.home_upload_file))
                                ) { _, auswahl ->
                                    when (auswahl) {
                                        0 -> scannerStarten()
                                        1 -> kategorieFuerImportWaehlen()
                                    }
                                }
                                .setNegativeButton(getString(R.string.home_cancel), null)
                                .show()
                        }
                    }
                    .setNegativeButton(getString(R.string.home_cancel), null)
                    .show()
    }

    private fun startseiteAnzeigen() {
        if (!entsperrt) return
        startseiteAktiv = true

        val layout = grundLayout()

        val kopfzeile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        kopfzeile.addView(
            titelText("DokumentenPilot"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        kopfzeile.addView(Button(this).apply {
            text = "☰"
            contentDescription = getString(R.string.home_menu)
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { funktionsMenueAnzeigen() }
        })

        layout.addView(kopfzeile)

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.home_subtitle)
                textSize = 14f
                setTextColor(sekundaerTextfarbe)
                setPadding(dp(4), 0, 0, dp(4))
            }
        )

        layout.addView(TextView(this).apply {
            text = getString(R.string.home_archive_total, dokumente.size)
            textSize = 14f
            setTextColor(sekundaerTextfarbe)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        layout.addView(EditText(this).apply {
            hint = getString(R.string.home_search_hint)
            contentDescription = getString(R.string.home_search_hint)
            textSize = 16f
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = hintergrund(kartenFarbe, 16f)
            minimumHeight = dp(52)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    val anfrage = text.toString()
                    dokumentenAnsichtAnzeigen("Alle Kategorien")
                    suche.setText(anfrage)
                    true
                } else false
            }
        })

        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(dunkelblauerButton(getString(R.string.home_add_pdf)) { kategorieFuerImportWaehlen() },
                LinearLayout.LayoutParams(0, -1, 1f))
            addView(dunkelblauerButton(getString(R.string.home_scan)) { scannerStarten() },
                LinearLayout.LayoutParams(0, -1, 1f))
        })

        layout.addView(dunkelblauerButton(getString(R.string.home_folders)) { mappenStarten() })

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.home_categories)
                textSize = 21f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(8), 0, dp(6))
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

        eintraege.chunked(if (resources.configuration.screenWidthDp >= 600) 3 else 2).forEach { paar ->
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
                    val kachelfarben = if (dunklerModus) listOf(
                        Color.rgb(37, 49, 71), Color.rgb(41, 55, 57),
                        Color.rgb(56, 49, 67), Color.rgb(52, 53, 41)
                    ) else listOf(
                        Color.rgb(231, 240, 255), Color.rgb(231, 246, 240),
                        Color.rgb(242, 236, 255), Color.rgb(252, 244, 231)
                    )
                    background = hintergrund(
                        kachelfarben[kategorien.indexOf(kategorie).coerceAtLeast(0) %
                            kachelfarben.size], 18f
                    )
                    minimumHeight = dp(100)
                    contentDescription = "${kategorieAnzeigename(kategorie)}, ${anzahlFuer(kategorie)}"
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
                        textSize = 24f
                        gravity = Gravity.CENTER
                    }
                )

                karte.addView(
                    TextView(this).apply {
                        text = kategorieAnzeigename(kategorie)
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
                        setTextColor(sekundaerTextfarbe)
                        gravity = Gravity.CENTER
                    }
                )

                zeile.addView(
                    karte,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
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

        inhalt.addView(dunkelblauerButton(getString(R.string.home_new_category)) { neueKategorieDialog() })
        scroll.addView(inhalt)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        layout.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(dunkelblauerButton(getString(R.string.menu_assistants)) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.menu_assistants)
                    .setItems(arrayOf(getString(R.string.home_assistant), getString(R.string.home_collect))) { _, index ->
                        if (index == 0) assistentStarten() else unterlagenZusammenstellenStarten()
                    }
                    .setNegativeButton(R.string.home_cancel, null).show()
            })
        })
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
            hint = getString(R.string.ui_text_023)
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
            .setTitle(getString(R.string.ui_text_024))
            .setMessage(
                "Gib einen Namen für dein " +
                "gescanntes Dokument ein."
            )
            .setView(container)
            .setPositiveButton(getString(R.string.ui_text_003), null)
            .setNegativeButton(getString(R.string.ui_text_000), null)
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
                    .setTitle(getString(R.string.ui_text_025))
                    .setItems(
                        kategorien.toTypedArray()
                    ) { _, position ->
                        try {
                            val dokumentId = dokumentVerschluesseln(
                                dateiname,
                                pdfDaten,
                                kategorien[position]
                            )

                            val anfrageMappeFuerScan = aktiveAnfrageMappeId
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

                            meldung(getString(R.string.ui_text_026))
                            importOcrStarten(dokumentId, pdfDaten, anfrageMappeFuerScan)
                        } catch (e: Exception) {
                            meldung(
                                "Scan konnte nicht gespeichert werden"
                            )
                        }
                    }
                    .setNegativeButton(
                        getString(R.string.ui_text_000),
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
                getString(R.string.home_add_pdf)
            ) {
                if (kategorie == "Alle Kategorien") kategorieFuerImportWaehlen()
                else importStarten(kategorie)
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
            text = getString(R.string.ui_text_027)
            textSize = 12f
            setTextColor(sekundaerTextfarbe)
            setPadding(dp(4), dp(6), 0, dp(8))
        }

        layout.addView(ocrAnzeige)

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.ui_text_028)
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
            hint = getString(R.string.ui_text_029)
            textSize = 15f
            setSingleLine(true)
            setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
            )
            background = hintergrund(
                kartenFarbe,
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

        layout.addView(dunkelblauerButton(getString(R.string.home_back_full)) { startseiteAnzeigen() })
        setContentView(layout)

        ocrStatusAktualisieren()
        listeAktualisieren()
    }

    private fun kategorieFuerImportWaehlen() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_text_025))
            .setItems(
                kategorien.toTypedArray()
            ) { _, position ->
                importStarten(kategorien[position])
            }
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    private fun importStarten(kategorie: String) {
                importKategorie = kategorie

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
                    meldung(getString(R.string.ui_text_030))
                }
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
            .setTitle(getString(R.string.ui_text_031))
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
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    // VERSCHLÜSSELUNG
    // Schlüsselname und Dateiformat bleiben unverändert.

    @Synchronized
    private fun schluesselHolen(erzeugenErlaubt: Boolean = true): SecretKey {
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

        // Nie stillschweigend einen Ersatzschlüssel erzeugen, solange
        // verschlüsselte Dokumente vorhanden sind. Sonst wären bestehende
        // Dateien nach weiteren Speichervorgängen nicht mehr zuzuordnen.
        val verschluesselteDaten = listOf("dokumente", "ocr_index").any { name ->
            val ordner = File(filesDir, name)
            ordner.exists() && (ordner.listFiles()?.any { it.isFile } != false)
        }
        if (!erzeugenErlaubt || verschluesselteDaten) {
            throw IllegalStateException(
                "Vorhandene Dokumente können ohne ihren ursprünglichen AndroidKeyStore-Schlüssel nicht geöffnet werden"
            )
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

    private fun datenVerschluesseln(daten: ByteArray): ByteArray =
        ArchivKryptografie.verschluesseln(daten, schluesselHolen())

    private fun datenEntschluesseln(daten: ByteArray): ByteArray =
        ArchivKryptografie.entschluesseln(daten, schluesselHolen(erzeugenErlaubt = false))

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

    private fun ocrTextSpeichern(dokumentId: String, text: String) {
        val daten = datenVerschluesseln(text.toByteArray(Charsets.UTF_8))
        val datei = android.util.AtomicFile(ocrDatei(dokumentId))
        val ausgabe = datei.startWrite()
        try {
            ausgabe.write(daten)
            datei.finishWrite(ausgabe)
        } catch (e: Exception) {
            datei.failWrite(ausgabe)
            throw e
        }
    }

    private fun importOcrStarten(dokumentId: String, pdfDaten: ByteArray, anfrageMappeId: String?) {
        laufendeImportOcr.add(dokumentId)
        Thread {
            try {
                val text = OcrHilfe.textAusPdf(applicationContext, pdfDaten)
                ocrTextSpeichern(dokumentId, text)
                val anforderungen = if (anfrageMappeId != null) {
                    UnterlagenErkennung.anforderungenErkennen(text)
                } else emptyList()

                runOnUiThread {
                    laufendeImportOcr.remove(dokumentId)
                    if (anfrageMappeId != null && anforderungen.isNotEmpty()) {
                        val verwaltung = UnterlagenMappen(this)
                        val mappen = verwaltung.laden()
                        val mappe = mappen.find { it.id == anfrageMappeId }
                        if (mappe != null) {
                            for (anforderung in anforderungen) {
                                val art = anforderung.dokumentenart
                                mappe.anforderungen.removeAll { it.dokumentenart == art }
                                mappe.anforderungen.add(anforderung)
                                if (art !in mappe.angeforderteUnterlagen) {
                                    mappe.angeforderteUnterlagen.add(art)
                                }
                            }
                            verwaltung.speichern(mappen)
                        }
                    }
                    if (entsperrt) {
                        ocrTexte[dokumentId] = text
                        if (!startseiteAktiv) listeAktualisieren()
                        ocrStatusAktualisieren()
                        if (anfrageMappeId != null) {
                            meldung(
                                if (anforderungen.isEmpty()) "Keine Unterlagenanforderungen erkannt"
                                else "${anforderungen.size} Unterlagenanforderungen erkannt"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    laufendeImportOcr.remove(dokumentId)
                    if (entsperrt) meldung(getString(R.string.ui_text_044))
                }
            }
        }.start()
    }

    private fun ocrStatusAktualisieren() {
        if (
            startseiteAktiv ||
            !::ocrAnzeige.isInitialized ||
            !::ocrButton.isInitialized
        ) {
            return
        }

        val anzahl = dokumente.count { ocrTexte.containsKey(it.first) }

        ocrAnzeige.text = if (ocrIndexLaedt) {
            "Textsuche wird geladen ..."
        } else if (ocrLaeuft) {
            "Texterkennung läuft im Hintergrund ..."
        } else {
            "$anzahl von ${dokumente.size} Dokumenten für die Textsuche erfasst"
        }

        ocrButton.isEnabled = !ocrLaeuft && !ocrIndexLaedt
    }

    private fun ocrIndexLaden() {
        val generation = ++ocrGeneration
        ocrIndexLaedt = true

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
                        val text = String(
                            datenEntschluesseln(datei.readBytes()),
                            Charsets.UTF_8
                        )
                        if (text.isNotBlank()) geladeneTexte[name] = text
                    } catch (e: Exception) {
                        // Ein fehlerhafter OCR-Index
                        // darf die PDFs nicht beeinflussen.
                    }
                }
            }

            runOnUiThread {
                if (entsperrt && generation == ocrGeneration) {
                    ocrIndexLaedt = false
                    dokumente.forEach { (name, _) ->
                        geladeneTexte[name]?.let { ocrTexte.putIfAbsent(name, it) }
                    }

                    if (!startseiteAktiv) {
                        listeAktualisieren()
                        ocrStatusAktualisieren()
                    }
                }
            }
        }.start()
    }

    private fun ocrFuerAlleStarten() {
        if (ocrLaeuft || ocrIndexLaedt) {
            meldung(getString(R.string.ui_text_032))
            return
        }

        val nochNichtErfasst = dokumente
            .map { it.first }
            .filter {
                it !in ocrTexte && it !in laufendeImportOcr
            }

        if (nochNichtErfasst.isEmpty()) {
            meldung(
                if (laufendeImportOcr.isEmpty()) "Alle Dokumente wurden bereits erfasst"
                else "Texterkennung läuft im Hintergrund ..."
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_text_033))
            .setMessage(
                "${nochNichtErfasst.size} Dokument(e) " +
                "werden auf diesem Gerät verarbeitet.\n\n" +
                "Das kann bei vielen PDF-Seiten " +
                "einige Minuten dauern."
            )
            .setPositiveButton(getString(R.string.ui_text_011)) { _, _ ->
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

                            ocrTextSpeichern(name, text)

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
                                getString(R.string.ui_text_043)
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
            .setNegativeButton(getString(R.string.ui_text_000), null)
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
            .setTitle(getString(R.string.ui_text_034))
            .setView(container)
            .setPositiveButton(getString(R.string.ui_text_004), null)
            .setNegativeButton(getString(R.string.ui_text_000), null)
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
                    meldung(getString(R.string.ui_text_035))

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
            .setTitle(getString(R.string.ui_text_036))
            .setMessage(
                "Die ausgewählten PDFs werden zum Teilen " +
                "vorübergehend entschlüsselt. Die empfangende " +
                "App erhält unverschlüsselte Kopien.\\n\\n" +
                "Möchtest du fortfahren?"
            )
            .setPositiveButton(getString(R.string.ui_text_003)) { _, _ ->
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
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    private fun dokumentTeilen(
        dateiName: String
    ) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_text_037))
            .setMessage(
                "Beim Teilen wird eine entschlüsselte " +
                "Kopie der PDF an die ausgewählte App " +
                "weitergegeben. Dort ist sie nicht mehr " +
                "durch DokumentenPilot geschützt.\n\n" +
                "Möchtest du fortfahren?"
            )
            .setPositiveButton(getString(R.string.ui_text_003)) { _, _ ->
                try {
                    val pdfDatei =
                        run {
                                                val originalName = dokumente.find {
                                                    it.first == dateiName
                                                }?.second ?: "Dokument"

                                                val sichererName = originalName
                                                    .substringAfterLast('/')
                                                    .substringAfterLast('\\')
                                                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                                                    .ifBlank { "Dokument" }

                                                val pdfName = if (
                                                    sichererName.endsWith(".pdf", ignoreCase = true)
                                                ) sichererName else "$sichererName.pdf"

                                                val ordner = File(cacheDir, "pdf_einzelversand")
                                                ordner.mkdirs()

                                                val datei = File(ordner, pdfName)
                                                datei.writeBytes(dokumentEntschluesseln(dateiName))
                                                datei
                                            }

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
            .setNegativeButton(getString(R.string.ui_text_000), null)
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

                val kategoriePasst =
                    aktiveKategorie == "Alle Kategorien" ||
                    kategorieVon(dokument.first) ==
                        aktiveKategorie

                ArchivAnzeige.passt(kategoriePasst, dokument.second, ocrTexte[dokument.first].orEmpty(), suchtext)
            }

        anzahlAnzeige.text =
            "Dokumente: ${sichtbareDokumente.size}"

        if (sichtbareDokumente.isEmpty()) {
            dokumentenListe.addView(
                TextView(this).apply {
                    text = getString(R.string.ui_text_038)
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
                        text = getString(R.string.ui_text_039)
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
                dunkelblauerButton(getString(R.string.ui_text_012)) {
                    dokumentOeffnen(dokument.first)
                },
                knopfParameter()
            )

            ersteZeile.addView(
                Button(this).apply {
                    text = getString(R.string.ui_text_013)
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
                    text = getString(R.string.ui_text_014)
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
                    text = getString(R.string.ui_text_015)
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
                    text = getString(R.string.ui_text_006)
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
                                getString(R.string.ui_text_006)
                            ) { _, _ ->
                                dokumentLoeschen(
                                    dokument.first
                                )
                            }
                            .setNegativeButton(
                                getString(R.string.ui_text_000),
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
                ocrIndexLaden()
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
                    meldung(getString(R.string.ui_text_040))
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

            meldung(getString(R.string.ui_text_041))

            importOcrStarten(dokumentId, speicherDaten, anfrageMappeFuerOcr)

        } catch (e: Exception) {
            meldung(getString(R.string.ui_text_045))
        }
    }

    // DOKUMENT LÖSCHEN

    private fun dokumentLoeschen(
        dateiName: String
    ) {
        if (ocrLaeuft || dateiName in laufendeImportOcr) {
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

            meldung(getString(R.string.ui_text_046))

        } catch (e: Exception) {
            meldung(getString(R.string.ui_text_047))
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
