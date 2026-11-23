
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import de.dokumentenpilot.app.DokumentKrypto.datenEntschluesseln
import de.dokumentenpilot.app.DokumentKrypto.datenVerschluesseln

class MainActivity : Activity() {

    private val PDF_AUSWAHL = 100
    private val SCAN_AUSWAHL = 101
    private var aktiveAnfrageMappeId: String? = null

    private var entsperrt = false
    private var anmeldungLaeuft = false
    private var pinAnmeldungLaeuft = false
    private var appImVordergrund = false
    private var ausstehendesErgebnis: Triple<Int, Int, Intent?>? = null

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
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        pdfCache.alteDateienEntfernen()
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::zurueckNavigieren
            )
        }
        sperrbildschirmAnzeigen()
    }

    override fun onResume() {
        super.onResume()
        appImVordergrund = true
        if (entsperrt) {
            ausstehendesErgebnisVerarbeiten()
        } else if (!anmeldungLaeuft && !pinAnmeldungLaeuft) {
            window.decorView.post { anmeldungStarten() }
        }
    }

    override fun onStop() {
        super.onStop()
        appImVordergrund = false
        entsperrt = false
        ++ocrGeneration
        ocrTexte.clear()
        offeneDialoge.toList().forEach { it.dismiss() }
        offeneDialoge.clear()
        sperrbildschirmAnzeigen()
    }

    private val offeneDialoge = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<AlertDialog, Boolean>()
    )

    private fun zugriffErlaubt() = entsperrt && appImVordergrund && !isFinishing && !isDestroyed

    internal fun dialogBuilder(): AlertDialog.Builder = object : AlertDialog.Builder(this) {
        override fun create(): AlertDialog = super.create().also {
            it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            offeneDialoge.add(it)
        }

        override fun show(): AlertDialog = create().also { if (zugriffErlaubt()) it.show() }
    }

    private fun ausstehendesErgebnisVerarbeiten() {
        if (!zugriffErlaubt()) return
        val ergebnis = ausstehendesErgebnis ?: return
        ausstehendesErgebnis = null
        ergebnisVerarbeiten(ergebnis.first, ergebnis.second, ergebnis.third)
    }

    private fun nachAnmeldung() {
        try {
            if (File(filesDir, "dokumente").list()?.any { it.endsWith(".dp") } == true) {
                DokumentKrypto.archivSchluesselPruefen()
            }
            kategorienLaden()
            dokumenteLaden()
        } catch (e: Exception) {
            entsperrt = false
            sperrbildschirmAnzeigen()
            meldung(getString(R.string.storage_unavailable))
            return
        }
        runCatching { UnterlagenMappen(this).laden() }
        entsperrt = true
        ocrIndexLaden()
        if (startseiteAktiv) startseiteAnzeigen() else dokumentenAnsichtAnzeigen(aktiveKategorie)
        ausstehendesErgebnisVerarbeiten()
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
                nachAnmeldung()
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
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val rand = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(dp(16) + rand.left, dp(20) + rand.top, dp(16) + rand.right, dp(90) + rand.bottom)
                insets
            }
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

        val gespeichert = kategorienDatei.lesen {
            kategorieEinstellungen.getString("liste", null)
        }
        kategorieEinstellungen.edit().remove("liste").commit()

        val zuordnungen = zuordnungenDatei.lesen {
            if (einstellungen.all.isEmpty()) null else org.json.JSONObject(einstellungen.all).toString()
        }
        dokumentKategorien.clear()
        if (zuordnungen != null) {
            val json = org.json.JSONObject(zuordnungen)
            json.keys().forEach { dokumentKategorien[it] = json.getString(it) }
        }
        einstellungen.edit().clear().commit()

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

    private val kategorienDatei by lazy { GeschuetzteDatei(File(filesDir, "kategorien.dp")) }
    private val zuordnungenDatei by lazy { GeschuetzteDatei(File(filesDir, "kategorie_zuordnungen.dp")) }
    private val dokumentKategorien = mutableMapOf<String, String>()

    private fun kategorienSpeichern() {
        kategorienDatei.schreiben(kategorien.joinToString("\n"))
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

        val dialog = dialogBuilder()
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
        dialogBuilder()
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

        val dialog = dialogBuilder()
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

        dialogBuilder()
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

        dialogBuilder()
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
            dunkelblauerButton(getString(R.string.privacy_title)) {
                dialogBuilder()
                    .setTitle(R.string.privacy_title)
                    .setMessage(R.string.privacy_notice)
                    .setPositiveButton(R.string.ui_text_001, null)
                    .show()
            }
        )

        layout.addView(
            dunkelblauerButton("← " + getString(R.string.settings_back)) {
                startseiteAnzeigen()
            }
        )

        setContentView(layout)
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

        kopfzeile.addView(
            TextView(this).apply {
                text = "⚙"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(dunkelblau)
                contentDescription = "Einstellungen"
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    einstellungenAnzeigen()
                }
            }
        )

        layout.addView(kopfzeile)

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.home_subtitle)
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(dp(4), 0, 0, dp(18))
            }
        )

        layout.addView(
            dunkelblauerButton(
                getString(R.string.home_add_pdf)
            ) {
                kategorieFuerImportWaehlen()
            }
        )

        layout.addView(
            dunkelblauerButton(getString(R.string.home_assistant)) {
                if (ocrIndexLaedt) {
                    meldung("Textsuche wird geladen")
                    return@dunkelblauerButton
                }
                AssistentOberflaeche(
                    this,
                    dokumente,
                    ocrTexte,
                    { dateiName -> dokumentOeffnen(dateiName) }
                ).anzeigen()
            }
        )

        layout.addView(
            dunkelblauerButton(getString(R.string.home_collect)) {
                val nameEingabe = EditText(this).apply {
                    hint = getString(R.string.home_folder_hint)
                    setSingleLine(true)
                }

                dialogBuilder()
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

                            dialogBuilder()
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
        )

        layout.addView(
            dunkelblauerButton(getString(R.string.home_folders)) {
                if (ocrIndexLaedt) {
                    meldung("Textsuche wird geladen")
                    return@dunkelblauerButton
                }
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
                text = getString(R.string.home_categories)
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
                getString(R.string.home_new_category)
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
                text = getString(R.string.home_scan)
                textSize = 18f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        )

        scanKarte.addView(
            TextView(this).apply {
                text = getString(R.string.home_scan_multiple)
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
                getString(R.string.home_show_all)
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
        if (!zugriffErlaubt()) return
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
                    if (!zugriffErlaubt()) return@addOnSuccessListener
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
        if (!zugriffErlaubt()) return
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

        val dialog = dialogBuilder()
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

                dialogBuilder()
                    .setTitle(getString(R.string.ui_text_025))
                    .setItems(
                        kategorien.toTypedArray()
                    ) { _, position ->
                        importSpeichern(dateiname, pdfDaten, kategorien[position], aktiveAnfrageMappeId)
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
            text = getString(R.string.ui_text_027)
            textSize = 12f
            setTextColor(Color.DKGRAY)
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
    }

    private fun kategorieFuerImportWaehlen() {
        dialogBuilder()
            .setTitle(getString(R.string.ui_text_025))
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
                    meldung(getString(R.string.ui_text_030))
                }
            }
            .setNegativeButton(getString(R.string.ui_text_000), null)
            .show()
    }

    private fun kategorieVon(
        dateiName: String
    ): String {
        return dokumentKategorien[dateiName] ?: "Allgemein"
    }

    private fun kategorieSpeichern(dateiName: String, kategorie: String) {
        val aktualisiert = dokumentKategorien + (dateiName to kategorie)
        zuordnungenDatei.schreiben(org.json.JSONObject(aktualisiert).toString())
        dokumentKategorien[dateiName] = kategorie
    }

    private fun kategorieAendern(
        dateiName: String
    ) {
        val aktuell = kategorieVon(dateiName)

        val position =
            kategorien.indexOf(aktuell)
                .coerceAtLeast(0)

        dialogBuilder()
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

    private fun dokumentVerschluesseln(daten: ByteArray): File {
        val datei = dokumentDatei("${UUID.randomUUID()}.dp")
        datei.parentFile?.mkdirs()
        try {
            datei.writeBytes(datenVerschluesseln(daten))
            return datei
        } catch (e: Exception) {
            datei.delete()
            throw e
        }
    }

    private fun bildZuPdf(daten: ByteArray): ByteArray {
        val optionen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(daten, 0, daten.size, optionen)
        require(optionen.outMimeType == "image/jpeg" || optionen.outMimeType == "image/png")
        require(optionen.outWidth > 0 && optionen.outHeight > 0)
        var abtastung = 1
        while (maxOf(optionen.outWidth, optionen.outHeight) / abtastung > 2048) abtastung *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            daten, 0, daten.size, BitmapFactory.Options().apply { inSampleSize = abtastung }
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

        val alt = File(filesDir, "dokumentnamen.txt")
        val text = dokumentnamen.lesen(alt) ?: return
        text.lineSequence().forEach { zeile ->
            val teile = zeile.split("|", limit = 2)
            if (teile.size == 2) dokumente.add(teile[0] to teile[1])
        }
        File(filesDir, "dokumentnamen_neu.tmp").delete()
    }

    private val dokumentnamen by lazy { GeschuetzteDatei(File(filesDir, "dokumentnamen.dp")) }

    private fun dokumentnamenSpeichern(eintraege: List<Pair<String, String>>) {
        dokumentnamen.schreiben(eintraege.joinToString("\n") { (id, name) -> "$id|$name" })
    }

    private fun dokumentDatei(id: String) = File(File(filesDir, "dokumente"), id)

    private fun dokumentEntschluesseln(dateiName: String): ByteArray =
        datenEntschluesseln(dokumentDatei(dateiName).readBytes())

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

    private fun importOcrStarten(dokumentId: String, anfrageMappeId: String?) {
        laufendeImportOcr.add(dokumentId)
        ocrExecutor.execute {
            try {
                val text = OcrHilfe.textAusPdf(applicationContext, dokumentEntschluesseln(dokumentId))
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
        }
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
        if (!zugriffErlaubt()) return
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

        dialogBuilder()
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

                ocrExecutor.execute {
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
                        dialogBuilder()
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
                }
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

        val dialog = dialogBuilder()
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
                    dokumentnamenSpeichern(dokumente.map {
                        if (it.first == dateiName) it.first to neuerName else it
                    })

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

    private val pdfCache by lazy { PdfFreigaben(applicationContext) }

    private fun mehrereDokumenteTeilen(dateiNamen: List<String>) {
        if (!zugriffErlaubt() || dateiNamen.isEmpty()) return
        val auswahl = dateiNamen.distinct().mapNotNull { id ->
            dokumente.firstOrNull { it.first == id }
        }
        if (auswahl.isEmpty()) return
        if (auswahl.size > 30 || auswahl.sumOf { dokumentDatei(it.first).length() } > 128L * 1024 * 1024) {
            meldung(getString(R.string.pdf_share_limit))
            return
        }

        dialogBuilder()
            .setTitle(R.string.ui_text_036)
            .setMessage(R.string.pdf_share_notice)
            .setPositiveButton(R.string.ui_text_003) { _, _ ->
                if (!zugriffErlaubt()) return@setPositiveButton
                val generation = ocrGeneration
                dateiAuftrag {
                    var ordner: File? = null
                    try {
                        val ziel = pdfCache.neuerOrdner()
                        ordner = ziel
                        val uris = ArrayList(auswahl.mapIndexed { index, (id, name) ->
                            val sicher = name.substringAfterLast('/').substringAfterLast('\\')
                                .replace(Regex("""[^\p{L}\p{N} ._-]"""), "_")
                                .take(100).ifBlank { "Dokument" }
                            val pdfName = if (sicher.endsWith(".pdf", true)) sicher else "$sicher.pdf"
                            File(ziel, "${index + 1}_$pdfName").also {
                                it.writeBytes(dokumentEntschluesseln(id))
                            }.let(pdfCache::uri)
                        })
                        runOnUiThread {
                            if (!zugriffErlaubt() || generation != ocrGeneration) {
                                pdfCache.entfernen(ziel)
                                return@runOnUiThread
                            }
                            try {
                                val intent = Intent(
                                    if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                                ).apply {
                                    type = "application/pdf"
                                    if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
                                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = android.content.ClipData.newUri(contentResolver, "PDF", uris.first())
                                        .apply {
                                            uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
                                        }
                                }
                                startActivity(Intent.createChooser(intent, getString(R.string.ui_text_036)))
                            } catch (e: Exception) {
                                pdfCache.entfernen(ziel)
                                meldung(getString(R.string.pdf_share_failed))
                            }
                        }
                    } catch (e: Exception) {
                        ordner?.let(pdfCache::entfernen)
                        runOnUiThread { if (zugriffErlaubt()) meldung(getString(R.string.pdf_share_failed)) }
                    }
                }
            }
            .setNegativeButton(R.string.ui_text_000, null)
            .show()
    }

    private fun dokumentTeilen(dateiName: String) = mehrereDokumenteTeilen(listOf(dateiName))

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

                kategoriePasst &&
                    AssistentSuche.ocrSuchePasst(
                        dokument.second, ocrTexte[dokument.first].orEmpty(), suchtext
                    )
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
                        this@MainActivity.dialogBuilder()
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

    private fun dokumentOeffnen(dateiName: String) {
        if (!zugriffErlaubt() || dokumente.none { it.first == dateiName }) return
        val generation = ocrGeneration
        dateiAuftrag {
            try {
                val renderer = PdfDateien.rendererOeffnen(applicationContext, dokumentEntschluesseln(dateiName))
                runOnUiThread {
                    if (!zugriffErlaubt() || generation != ocrGeneration) renderer.close()
                    else pdfVorschauAnzeigen(renderer)
                }
            } catch (e: Exception) {
                runOnUiThread { if (zugriffErlaubt()) meldung(getString(R.string.pdf_open_failed)) }
            }
        }
    }

    private fun pdfVorschauAnzeigen(renderer: android.graphics.pdf.PdfRenderer) {
        if (renderer.pageCount == 0) {
            renderer.close()
            meldung(getString(R.string.pdf_open_failed))
            return
        }
        val bild = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        var index = 0
        var bitmap: android.graphics.Bitmap? = null
        val dialog = dialogBuilder()
            .setView(bild)
            .setNeutralButton(R.string.pdf_previous_page, null)
            .setPositiveButton(R.string.pdf_next_page, null)
            .setNegativeButton(R.string.ui_text_001, null)
            .create()
        val geschlossen = java.util.concurrent.atomic.AtomicBoolean(false)
        fun anzeigen() {
            if (!zugriffErlaubt()) return
            val seite = index
            val zurueck = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val weiter = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            zurueck.isEnabled = false
            weiter.isEnabled = false
            dialog.setTitle(getString(R.string.pdf_page, seite + 1, renderer.pageCount))
            vorschauExecutor.execute {
                if (geschlossen.get()) return@execute
                val ergebnis = runCatching { renderer.openPage(seite).use(PdfDateien::rendern) }
                runOnUiThread {
                    val naechste = ergebnis.getOrNull()
                    if (geschlossen.get() || !zugriffErlaubt()) {
                        naechste?.recycle()
                    } else if (naechste == null) {
                        dialog.dismiss()
                        meldung(getString(R.string.pdf_open_failed))
                    } else {
                        bild.setImageBitmap(naechste)
                        bitmap?.recycle()
                        bitmap = naechste
                        zurueck.isEnabled = seite > 0
                        weiter.isEnabled = seite + 1 < renderer.pageCount
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            geschlossen.set(true)
            bild.setImageDrawable(null)
            bitmap?.recycle()
            vorschauExecutor.execute { renderer.close() }
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (index > 0) {
                index--
                anzeigen()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (index + 1 < renderer.pageCount) {
                index++
                anzeigen()
            }
        }
        anzeigen()
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

        if (requestCode == AppSperre.PIN_ANFRAGE) {
            val pinErwartet = pinAnmeldungLaeuft
            pinAnmeldungLaeuft = false
            anmeldungLaeuft = false
            if (pinErwartet && resultCode == RESULT_OK) nachAnmeldung()
            return
        }

        if (requestCode == SCAN_AUSWAHL || requestCode == PDF_AUSWAHL) {
            if (resultCode == RESULT_OK) {
                ausstehendesErgebnis = Triple(requestCode, resultCode, data)
                ausstehendesErgebnisVerarbeiten()
            } else {
                aktiveAnfrageMappeId = null
            }
        }
    }

    private fun ergebnisVerarbeiten(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!zugriffErlaubt() || resultCode != RESULT_OK) return
        val scan = requestCode == SCAN_AUSWAHL
        val uri = if (scan) GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf?.uri else data?.data
        if (uri?.scheme != "content") {
            meldung(getString(R.string.document_import_failed))
            return
        }
        val generation = ocrGeneration
        val kategorie = importKategorie
        val mappeId = aktiveAnfrageMappeId
        dateiAuftrag {
            try {
                val name = if (scan) "Scan.pdf" else contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Dokument.pdf"
                val dateiname = name.replace("\n", " ").replace("\r", " ").take(200)
                val daten = contentResolver.openInputStream(uri)?.use { PdfDateien.lesen(it) }
                    ?: error("Datei nicht lesbar")
                val istPdf = PdfDateien.istPdf(daten)
                val pdf = if (istPdf) daten else {
                    require(!scan)
                    bildZuPdf(daten)
                }
                require(pdf.size <= PdfDateien.MAX_BYTES)
                val pdfName = if (dateiname.endsWith(".pdf", true)) dateiname
                    else "${dateiname.substringBeforeLast(".")}.pdf"
                runOnUiThread {
                    if (!zugriffErlaubt() || generation != ocrGeneration) return@runOnUiThread
                    if (scan) scanSpeichernDialog(pdf)
                    else importSpeichern(pdfName, pdf, kategorie, mappeId)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (zugriffErlaubt() && generation == ocrGeneration) {
                        if (aktiveAnfrageMappeId == mappeId) aktiveAnfrageMappeId = null
                        meldung(getString(R.string.document_import_failed))
                    }
                }
            }
        }
    }

    private fun importSpeichern(name: String, pdf: ByteArray, kategorie: String, mappeId: String?) {
        if (!zugriffErlaubt()) return
        val generation = ocrGeneration
        dateiAuftrag {
            val datei = try {
                dokumentVerschluesseln(pdf)
            } catch (e: Exception) {
                runOnUiThread {
                    if (zugriffErlaubt() && generation == ocrGeneration) meldung(getString(R.string.ui_text_045))
                }
                return@dateiAuftrag
            }
            runOnUiThread {
                if (!zugriffErlaubt() || generation != ocrGeneration) datei.delete()
                else importRegistrieren(datei, name, kategorie, mappeId)
            }
        }
    }

    private fun importRegistrieren(datei: File, name: String, kategorie: String, mappeId: String?) {
        val eintrag = datei.name to name
        try {
            kategorieSpeichern(datei.name, kategorie)
            dokumentnamenSpeichern(dokumente + eintrag)
        } catch (e: Exception) {
            datei.delete()
            meldung(getString(R.string.ui_text_045))
            return
        }
        dokumente.add(eintrag)
        val zugeordneteMappe = if (mappeId == null) null else runCatching {
            val verwaltung = UnterlagenMappen(this)
            val mappen = verwaltung.laden()
            mappen.find { it.id == mappeId }?.also {
                it.dokumentIds.add(datei.name)
                verwaltung.speichern(mappen)
            }?.id
        }.getOrNull()
        if (aktiveAnfrageMappeId == mappeId) aktiveAnfrageMappeId = null

        if (startseiteAktiv) startseiteAnzeigen() else {
            listeAktualisieren()
            ocrStatusAktualisieren()
        }
        meldung(getString(
            if (mappeId != null && zugeordneteMappe == null) R.string.document_saved_without_folder
            else R.string.ui_text_041
        ))
        importOcrStarten(datei.name, zugeordneteMappe)
    }

    private fun dateiAuftrag(arbeit: () -> Unit) {
        try {
            dateiExecutor.execute(arbeit)
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            meldung(getString(R.string.document_busy))
        }
    }

    private companion object {
        val dateiExecutor = java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(2)
        )
        val ocrExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val vorschauExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }

    // DOKUMENT LÖSCHEN

    private fun dokumentLoeschen(
        dateiName: String
    ) {
        if (!zugriffErlaubt()) return
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

            val verbleibend = dokumente.filter { it.first != dateiName }
            dokumentnamenSpeichern(verbleibend)
            if (!datei.delete() && datei.exists()) {
                dokumentnamenSpeichern(dokumente)
                meldung("Datei konnte nicht gelöscht werden")
                return
            }

            // Zugehörigen OCR-Index ebenfalls entfernen
            ocrDatei(dateiName).delete()
            ocrTexte.remove(dateiName)

            dokumentKategorien.remove(dateiName)
            zuordnungenDatei.schreiben(org.json.JSONObject(dokumentKategorien).toString())

            dokumenteLaden()
            listeAktualisieren()
            ocrStatusAktualisieren()

            meldung(getString(R.string.ui_text_046))

        } catch (e: Exception) {
            meldung(getString(R.string.ui_text_047))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = zurueckNavigieren()

    private fun zurueckNavigieren() {
        if (entsperrt && !startseiteAktiv) startseiteAnzeigen()
        else finish()
    }
}
