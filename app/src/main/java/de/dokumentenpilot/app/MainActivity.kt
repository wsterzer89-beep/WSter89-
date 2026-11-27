
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
import android.text.InputType
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
    private val mappenImportKategorie = MappenImportRegeln.KATEGORIE

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
    private var startseitenArbeitsanzeige: android.widget.ProgressBar? = null

    private var importVorbereitungLaeuft = false

    private fun startseitenArbeitsstatusAktualisieren() {
        startseitenArbeitsanzeige?.visibility = if (startseiteAktiv && entsperrt &&
            (importVorbereitungLaeuft || ocrIndexLaedt || ocrLaeuft || laufendeImportOcr.isNotEmpty())) View.VISIBLE else View.GONE
    }

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

    private var familienLinkAusstehend: String? = null
    private var familienPilot: FamilienTestPilot? = null
    private var familienTeilen = false
    private var familienAktivierung: java.util.concurrent.CompletableFuture<Boolean>? = null
    private var familienDialog: AlertDialog? = null

    private fun familienAbbrechen() {
        familienTeilen = false
        familienAktivierung?.complete(false)
        familienAktivierung = null
        familienDialog?.dismiss()
        familienDialog = null
        familienPilot?.close()
        familienPilot = null
    }

    private fun familienAktivierungAnzeigen() {
        val antwort = familienAktivierung ?: return
        val pilot = familienPilot ?: return
        if (!entsperrt || !appImVordergrund || antwort.isDone) return
        if (!pilot.aktiv()) { familienAbbrechen(); meldung("Einladung abgelaufen oder WLAN gewechselt. Bitte neu erstellen."); return }
        familienDialog = AlertDialog.Builder(this)
            .setTitle("Testverbindung aktivieren")
            .setMessage("Der Link wurde vorbereitet. Aktiviere jetzt die Verbindung und bleibe in DokumentenPilot. Erst danach soll das andere Gerät die Einladung annehmen.")
            .setPositiveButton("Aktivieren") { _, _ -> antwort.complete(entsperrt && appImVordergrund && pilot.aktiv()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> antwort.complete(false) }
            .setOnDismissListener { antwort.complete(false) }
            .show()
    }

    private fun familienBestaetigen(pilot: FamilienTestPilot, lokal: String, partner: String): Boolean {
        val antwort = java.util.concurrent.CompletableFuture<Boolean>()
        runOnUiThread {
            if (!entsperrt || !appImVordergrund || familienPilot !== pilot || !pilot.aktiv()) {
                antwort.complete(false)
            } else {
                familienDialog = AlertDialog.Builder(this)
                    .setTitle("Testverbindung bestätigen")
                    .setMessage("Vergleiche beide vollständigen Fingerabdrücke persönlich oder über einen bereits vertrauenswürdigen Kanal. Dein Fingerabdruck muss auf dem anderen Handy als Partner erscheinen und umgekehrt.\n\nDieses Gerät:\n${lokal.chunked(8).joinToString(" ")}\n\nPartnergerät:\n${partner.chunked(8).joinToString(" ")}\n\nNur bei Übereinstimmung bestätigen. Es werden ausschließlich künstliche Testdaten übertragen.")
                    .setPositiveButton("Bestätigen") { _, _ -> antwort.complete(entsperrt && appImVordergrund && pilot.aktiv()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> antwort.complete(false) }
                    .setOnDismissListener { antwort.complete(false) }
                    .show()
            }
        }
        return try { antwort.get(60, java.util.concurrent.TimeUnit.SECONDS) }
        catch (_: Exception) { false }
    }

    private fun familienBeitreten(code: String) {
        if (!entsperrt || !appImVordergrund) return
        familienAbbrechen()
        val pilot = FamilienTestPilot(this)
        familienPilot = pilot
        Thread {
            val ergebnis = runCatching {
                pilot.wlanUeberwachen(getSystemService(android.net.ConnectivityManager::class.java))
                pilot.beitreten(code) { lokal, partner -> familienBestaetigen(pilot, lokal, partner) }
            }.getOrNull()
            runOnUiThread {
                if (familienPilot === pilot) {
                    familienAbbrechen()
                    if (entsperrt && appImVordergrund) meldung(if (ergebnis != null) "Einladung angenommen. Beide Geräte haben zugestimmt. Testdokument empfangen. Die Geräteidentität ist lokal gespeichert; die Sitzung ist beendet.\n\n$ergebnis" else "Testaustausch nicht abgeschlossen. Eine lokale Bestätigung kann bereits gespeichert sein. Prüfe das WLAN und erstelle bei Bedarf eine neue Einladung.")
                }
            }
        }.start()
    }

    private fun familienLinkMerken(eingang: android.content.Intent?) {
        if (eingang?.action != android.content.Intent.ACTION_VIEW) return
        familienLinkAusstehend = eingang.dataString?.let { FamilienTestLink.lesen(it) }
        eingang.data = null
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        familienLinkMerken(intent)
        familienLinkAnzeigen()
    }

    private fun familienLinkAnzeigen() {
        if (!entsperrt || !appImVordergrund || familienLinkAusstehend == null) return
        val code = familienLinkAusstehend ?: return
        familienLinkAusstehend = null
        familienDialog = AlertDialog.Builder(this)
            .setTitle("Familieneinladung erhalten")
            .setMessage("Nur im selben WLAN. Es werden keine privaten Dokumente übertragen.")
            .setPositiveButton("Annehmen") { _, _ -> familienBeitreten(code) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        familienLinkMerken(intent)
        Thread { OcrHilfe.alteTempDateienEntfernen(applicationContext) }.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        kategorienLaden()
        dokumenteLaden()
        sperrbildschirmAnzeigen()
    }

    override fun onResume() {
        super.onResume()
        appImVordergrund = true
        familienTeilen = false
        familienAktivierungAnzeigen()
        familienLinkAnzeigen()
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
        if (!familienTeilen) familienAbbrechen()

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
                familienAktivierungAnzeigen()
                familienLinkAnzeigen()
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

    private fun suchLupenButton(aktion: () -> Unit): android.widget.ImageButton =
        android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(dunkelblau)
            background = hintergrund(kartenFarbe, 16f)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.home_search_hint)
            setOnClickListener { aktion() }
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

        dialog.setOnDismissListener { if (aktiveAnfrageMappeId != null && !entsperrt) aktiveAnfrageMappeId = null }
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
            this, dokumente.filter { kategorieVon(it.first) != mappenImportKategorie }, ocrTexte,
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
            { ids -> mehrereDokumenteTeilen(ids) },
            { ids -> privateMappendokumenteBereinigen(ids) },
            { entsperrt && appImVordergrund }
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
        content.addView(action(R.string.home_show_all, "▤") { dokumentenAnsichtAnzeigen("Alle Kategorien") })
        content.addView(action(R.string.family_sharing_title, "♧") {
            familienTestAnzeigen()
        })
        content.addView(action(R.string.settings_title, "⚙") { einstellungenAnzeigen() })
        dialog.show()
    }

    private fun familienTestAnzeigen() {
        if (!entsperrt) return
        val kopplung = FamilienGeraeteVertrauen(this).kopplung()
        val status = when (kopplung) {
            FamilienGeraeteVertrauen.Kopplung.NICHT_GEKOPPELT -> "Nicht gekoppelt"
            FamilienGeraeteVertrauen.Kopplung.LOKAL_BESTAETIGT -> "Lokal bestätigt – Gegenstelle gespeichert"
            FamilienGeraeteVertrauen.Kopplung.GESPERRT -> "Dauerhaft gesperrt"
            FamilienGeraeteVertrauen.Kopplung.UNBEKANNT -> "Status nicht verfügbar"
        }
        val verbindung = if (familienPilot?.aktiv() == true)
            "WLAN-Testvorgang läuft (keine bestätigte Live-Verbindung)"
        else "Keine aktive Sitzung"
        AlertDialog.Builder(this)
            .setTitle("Meine Familie – Gerätetest\n$status\n$verbindung\nNur Testdaten, kein Archivzugriff")
            .setItems(arrayOf("Einladung erstellen und teilen", "Einladung annehmen (Link einfügen)", "Dokumentenrechte simulieren (nur lokal)", "Testgerät dauerhaft sperren", "Testdokumente für gekoppeltes Gerät freigeben", "Testarchiv, Mappen und Kategorien freigeben", "Private Testdokumente ausschließen")) { _, index ->
                if (!entsperrt || !appImVordergrund) return@setItems
                when (index) {
                    0 -> familienEinladen()
                    1 -> familienLinkEingeben()
                    2 -> familienSimulationAnzeigen()
                    3 -> familienGeraetWiderrufen()
                    4 -> familienGeraeteFreigabenAnzeigen()
                    5 -> familienUmfangAnzeigen()
                    6 -> familienAusnahmenAnzeigen()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienAusnahmenAnzeigen() {
        if (!entsperrt || !appImVordergrund) return
        val peer = FamilienGeraeteVertrauen(this).bestaetigterPeer()
        if (peer == null) { meldung("Zuerst ein Testgerät koppeln."); return }
        val store = FamilienGeraeteFreigaben(this)
        val ids = arrayOf("demo-1", "demo-2")
        val bisher = store.ausnahmen(peer)
        val auswahl = BooleanArray(ids.size) { ids[it] in bisher }
        AlertDialog.Builder(this)
            .setTitle("Private Ausnahmen – markierte Testdokumente bleiben gesperrt")
            .setMultiChoiceItems(arrayOf("Beispieldokument 1", "Beispieldokument 2"), auswahl) { _, i, checked -> auswahl[i] = checked }
            .setPositiveButton("Speichern") { _, _ ->
                if (entsperrt && appImVordergrund) {
                    val ok = store.ausnahmenSpeichern(peer, ids.filterIndexed { i, _ -> auswahl[i] }.toSet())
                    meldung(if (ok) "Private Testausnahmen gespeichert." else "Ausnahmen konnten nicht gespeichert werden.")
                }
            }.setNegativeButton("Abbrechen", null).show()
    }

    private fun familienUmfangAnzeigen() {
        if (!entsperrt || !appImVordergrund) return
        val peer = FamilienGeraeteVertrauen(this).bestaetigterPeer()
        if (peer == null) { meldung("Zuerst ein Testgerät koppeln."); return }
        val store = FamilienGeraeteFreigaben(this)
        val bereiche = listOf(
            Triple(FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV, "", setOf("demo-1", "demo-2")),
            Triple(FamilienBerechtigungen.Bereich.MAPPE, "familie", setOf("demo-1")),
            Triple(FamilienBerechtigungen.Bereich.KATEGORIE, "Demo", setOf("demo-1")),
            Triple(FamilienBerechtigungen.Bereich.KATEGORIE, "Privat", setOf("demo-2"))
        )
        val namen = arrayOf("Gesamtes Testarchiv", "Testmappe Familie", "Testkategorie Demo", "Testkategorie Privat")
        val regeln = store.lesen(peer)
        val beschriftungen = namen.mapIndexed { i, name ->
            val (bereich, id, _) = bereiche[i]
            val regel = regeln.firstOrNull { it.bereich == bereich && it.bereichId == id }
            name + when {
                regel == null -> " – keine eigene Freigabe"
                regel.neueDokumenteAutomatisch -> " – inklusive zukünftiger Dokumente"
                else -> " – ${regel.dokumentIdsBeiFreigabe.size} vorhandene Dokumente"
            }
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Freigabeumfang – nur Testdaten")
            .setItems(beschriftungen) { _, i ->
                if (!entsperrt || !appImVordergrund) return@setItems
                val (bereich, id, dokumente) = bereiche[i]
                AlertDialog.Builder(this).setTitle(namen[i])
                    .setItems(arrayOf("Aktuelle Dokumente freigeben", "Auch zukünftige Dokumente freigeben", "Diese Freigabe entfernen")) { _, option ->
                        if (entsperrt && appImVordergrund) {
                            val ok = store.umfangSpeichern(peer, bereich, id, dokumente, option == 1, option != 2)
                            meldung(if (ok) "Lokale Testfreigabe aktualisiert. Andere Freigaben bleiben bestehen." else "Freigabe konnte nicht gespeichert werden.")
                        }
                    }
                    .setNegativeButton("Abbrechen", null).show()
            }.setNegativeButton("Schließen", null).show()
    }

    private fun familienGeraeteFreigabenAnzeigen() {
        if (!entsperrt || !appImVordergrund) return
        val peer = FamilienGeraeteVertrauen(this).bestaetigterPeer()
        if (peer == null) {
            meldung("Zuerst ein Testgerät koppeln. Gesperrte Geräte erhalten keine Freigaben.")
            return
        }
        val store = FamilienGeraeteFreigaben(this)
        val ids = arrayOf("demo-1", "demo-2")
        val vorhandene = store.lesen(peer).map { it.bereichId }.toSet()
        val auswahl = BooleanArray(ids.size) { ids[it] in vorhandene }
        AlertDialog.Builder(this)
            .setTitle("Testdokumente – lokale Freigaben")
            .setMultiChoiceItems(arrayOf("Beispieldokument 1", "Beispieldokument 2"), auswahl) { _, i, checked ->
                auswahl[i] = checked
            }
            .setPositiveButton("Speichern") { _, _ ->
                if (entsperrt && appImVordergrund) {
                    val ok = ids.indices.map { store.speichern(peer, ids[it], auswahl[it]) }.all { it }
                    meldung(if (ok) "Lokale Testfreigaben gespeichert. Noch keine Dokumentenübertragung." else "Nicht alle Freigaben konnten gespeichert werden. Bitte erneut prüfen.")
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienGeraetWiderrufen() {
        if (!entsperrt || !appImVordergrund) return
        AlertDialog.Builder(this)
            .setTitle("Testgerät dauerhaft sperren?")
            .setMessage("Die WLAN-Testverbindung wird beendet. Weitere Geräteverbindungen werden in dieser Installation gesperrt. Lokale Testprofile und das Archiv bleiben unverändert. Eine erneute Freigabe ist derzeit nicht vorgesehen.")
            .setPositiveButton("Dauerhaft sperren") { _, _ ->
                if (entsperrt && appImVordergrund) {
                    familienAbbrechen()
                    val ok = runCatching { FamilienGeraeteVertrauen(this).widerrufen() }.getOrDefault(false)
                    meldung(if (ok) "Geräteverbindungen dauerhaft gesperrt." else "Sperre konnte nicht gespeichert werden.")
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienEinladen() {
        if (!entsperrt || !appImVordergrund) return
        if (android.os.Build.VERSION.SDK_INT < 29) {
            meldung("Der Geräte-Test benötigt Android 10 oder neuer.")
            return
        }
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        val netz = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(netz)
        val adresse = if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
            cm.getLinkProperties(netz)?.linkAddresses?.map { it.address }
                ?.filterIsInstance<java.net.Inet4Address>()?.firstOrNull { it.isSiteLocalAddress }?.hostAddress
            else null
        if (adresse == null) {
            meldung("Bitte mit einem privaten WLAN verbinden.")
            return
        }
        familienAbbrechen()
        val pilot = FamilienTestPilot(this)
        familienPilot = pilot
        val aktivierung = java.util.concurrent.CompletableFuture<Boolean>()
        familienAktivierung = aktivierung
        Thread {
            val erfolg = runCatching {
                pilot.wlanUeberwachen(cm)
                pilot.einladen(adresse, { code ->
                    runOnUiThread {
                        if (familienPilot === pilot && pilot.aktiv() && entsperrt && appImVordergrund) {
                            familienTeilen = true
                            try { startActivity(FamilienTestLink.teilen(code)) }
                            catch (_: Exception) { familienAbbrechen() }
                        } else pilot.close()
                    }
                }, aktivieren = {
                    try { aktivierung.get(90, java.util.concurrent.TimeUnit.SECONDS) }
                    catch (_: Exception) { false }
                }) { lokal, partner -> familienBestaetigen(pilot, lokal, partner) }
            }.getOrDefault(false)
            runOnUiThread {
                if (familienPilot === pilot) {
                    familienAbbrechen()
                    if (entsperrt && appImVordergrund) meldung(if (erfolg) "Beide Geräte haben zugestimmt. Testdokument gesendet und Empfang vom Partner bestätigt. Die Geräteidentität ist lokal gespeichert; die Sitzung ist beendet." else "Testaustausch nicht abgeschlossen. Prüfe den Kopplungsstatus in Meine Familie.")
                }
            }
        }.start()
    }

    private fun familienLinkEingeben() {
        if (!entsperrt || !appImVordergrund) return
        val eingabe = EditText(this).apply {
            hint = "Einladungslink"
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(256))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        AlertDialog.Builder(this)
            .setTitle("Einladungslink einfügen")
            .setView(eingabe)
            .setPositiveButton("Prüfen") { _, _ ->
                if (entsperrt && appImVordergrund) {
                    familienLinkAusstehend = FamilienTestLink.lesen(eingabe.text.toString().trim())
                    eingabe.text.clear()
                    if (familienLinkAusstehend == null) meldung("Ungültiger Einladungslink")
                    else familienLinkAnzeigen()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienSimulationAnzeigen() {
        if (!entsperrt) return
        val demo = FamilienBerechtigungen.Dokument("demo-1", "demo-owner", "Demo")
        val privat = FamilienBerechtigungen.Dokument("demo-2", "demo-owner", "Privat")
        val profile = FamilienTestProfile(this)
        var freigaben = profile.freigaben()
        var einladung: FamilienEinladung.Einladung? = if (profile.vorhanden())
            FamilienEinladung.Einladung("local-demo-profile", "demo-owner", "demo-member", 0L, FamilienEinladung.Status.ANGENOMMEN)
        else null
        fun anzeigen() {
            if (!entsperrt) return
            val sichtbar = FamilienBerechtigungen.sichtbareDokumente("demo-member", listOf(demo, privat), freigaben)
            val status = einladung?.status?.let {
                when (it) {
                    FamilienEinladung.Status.OFFEN -> "Offen"
                    FamilienEinladung.Status.ANGENOMMEN -> "Angenommen"
                    FamilienEinladung.Status.WIDERRUFEN -> "Widerrufen"
                }
            } ?: "Keine Einladung"
            val optionen = mutableListOf<Pair<String, () -> Unit>>()
            if (einladung == null || einladung?.status == FamilienEinladung.Status.WIDERRUFEN) {
                optionen.add("Test-Familienmitglied einladen" to {
                    einladung = FamilienEinladung.Einladung(java.util.UUID.randomUUID().toString(), "demo-owner", "demo-member", System.currentTimeMillis() + 600000)
                })
            }
            if (einladung?.status == FamilienEinladung.Status.OFFEN) {
                optionen.add("Als Testmitglied Einladung annehmen" to {
                    einladung?.let { FamilienEinladung.annehmen(it, "demo-member", System.currentTimeMillis()) }?.let {
                        if (profile.speichern()) einladung = it
                        else meldung("Testprofil konnte nicht gespeichert werden.")
                    }
                })
            }
            if (einladung?.status == FamilienEinladung.Status.ANGENOMMEN) {
                listOf(demo, privat).forEach { dokument ->
                    val regel = freigaben.firstOrNull { it.bereichId == dokument.id }
                    optionen.add("${dokument.kategorie}: Berechtigungen festlegen" to {
                        val aktionen = listOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN,
                            FamilienBerechtigungen.Aktion.EXPORTIEREN, FamilienBerechtigungen.Aktion.BEARBEITEN,
                            FamilienBerechtigungen.Aktion.LOESCHEN)
                        val namen = arrayOf("Ansehen", "Suchen", "Exportieren", "Bearbeiten", "Löschen")
                        val gewaehlt = BooleanArray(aktionen.size) { aktionen[it] in (regel?.erlaubteAktionen ?: emptySet()) }
                        AlertDialog.Builder(this)
                            .setTitle("Testrechte: ${dokument.kategorie}")
                            .setMultiChoiceItems(namen, gewaehlt) { _, index, aktiv -> gewaehlt[index] = aktiv }
                            .setPositiveButton("Speichern") { _, _ ->
                                if (entsperrt && appImVordergrund && einladung?.status == FamilienEinladung.Status.ANGENOMMEN) {
                                    val rechte = aktionen.filterIndexed { index, _ -> gewaehlt[index] }.toSet()
                                    if (!profile.rechteSpeichern(dokument.id, rechte))
                                        meldung("Testrechte konnten nicht gespeichert werden.")
                                    freigaben = profile.freigaben()
                                    anzeigen()
                                }
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> anzeigen() }
                            .show()
                    })
                    if (regel != null) optionen.add("${dokument.kategorie}: Freigabe widerrufen" to {
                        if (!profile.rechteSpeichern(dokument.id, emptySet()))
                            meldung("Testrechte konnten nicht entfernt werden.")
                        freigaben = profile.freigaben()
                    })
                }
            }
            if (einladung != null && einladung?.status != FamilienEinladung.Status.WIDERRUFEN) {
                optionen.add("Einladung und Freigabe widerrufen" to {
                    if (profile.entfernen()) {
                        freigaben = emptyList()
                        einladung?.let { FamilienEinladung.widerrufen(it, "demo-owner") }?.let { einladung = it }
                    } else meldung("Testprofil konnte nicht entfernt werden.")
                })
            }
            AlertDialog.Builder(this)
                .setTitle("Familien-Testmodus (nur lokal)\nEinladung: $status\nSichtbare Beispieldokumente: ${sichtbar.size} / 2")
                .setItems(optionen.map { it.first }.toTypedArray()) { _, index ->
                    if (entsperrt) {
                        optionen[index].second()
                        if (!optionen[index].first.endsWith(": Berechtigungen festlegen")) anzeigen()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        anzeigen()
    }

    private fun unterlagenZusammenstellenStarten() {
        if (!entsperrt || importVorbereitungLaeuft) return
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
                                1 -> importStarten(mappenImportKategorie)
                            }
                        }
                        .setNegativeButton(getString(R.string.home_cancel)) { _, _ -> aktiveAnfrageMappeId = null }
                        .setOnCancelListener { aktiveAnfrageMappeId = null }
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
        startseitenArbeitsanzeige = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            contentDescription = getString(R.string.ui_text_032)
            visibility = View.GONE
        }
        layout.addView(startseitenArbeitsanzeige, LinearLayout.LayoutParams(-1, dp(8)))
        startseitenArbeitsstatusAktualisieren()

        layout.addView(
            TextView(this).apply {
                text = getString(R.string.home_subtitle)
                textSize = 14f
                setTextColor(sekundaerTextfarbe)
                setPadding(dp(4), 0, 0, dp(4))
            }
        )

        layout.addView(TextView(this).apply {
            text = getString(R.string.home_archive_total, dokumente.count { kategorieVon(it.first) != mappenImportKategorie })
            textSize = 14f
            setTextColor(sekundaerTextfarbe)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        val startSuche = EditText(this).apply {
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
                    dokumentenAnsichtAnzeigen("Alle Kategorien", anfrage)
                    true
                } else false
            }
        }
        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startSuche, LinearLayout.LayoutParams(0, -2, 1f))
            addView(suchLupenButton() {
                dokumentenAnsichtAnzeigen("Alle Kategorien", startSuche.text.toString())
            }.apply { contentDescription = getString(R.string.home_search_hint) },
                LinearLayout.LayoutParams(dp(56), -1))
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
        if (!entsperrt || importVorbereitungLaeuft) return
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
            .setNegativeButton(getString(R.string.ui_text_000)) { _, _ -> aktiveAnfrageMappeId = null }
            .setOnCancelListener { aktiveAnfrageMappeId = null }
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

                fun speichern(kategorie: String) {
                        if (!entsperrt || isFinishing || isDestroyed) {
                            aktiveAnfrageMappeId = null
                            return
                        }
                        try {
                            val zielMappe = aktiveAnfrageMappeId
                            if (zielMappe != null && UnterlagenMappen(this).laden().none { it.id == zielMappe }) {
                                aktiveAnfrageMappeId = null
                                meldung(getString(R.string.ui_text_045))
                                return
                            }
                            val dokumentId = dokumentVerschluesseln(
                                dateiname,
                                pdfDaten,
                                kategorie
                            )

                            val anfrageMappeFuerScan = aktiveAnfrageMappeId
                            aktiveAnfrageMappeId = null
                            anfrageMappeFuerScan?.let { mappeId ->
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
                            aktiveAnfrageMappeId = null
                            meldung(
                                "Scan konnte nicht gespeichert werden"
                            )
                        }
                    }
                if (aktiveAnfrageMappeId != null) speichern(mappenImportKategorie)
                else AlertDialog.Builder(this)
                    .setTitle(getString(R.string.ui_text_025))
                    .setItems(kategorien.toTypedArray()) { _, position -> speichern(kategorien[position]) }
                    .setNegativeButton(getString(R.string.ui_text_000), null).show()
            }
        }

        dialog.show()
    }

    // DOKUMENTENANSICHT

    private fun dokumentenAnsichtAnzeigen(
        kategorie: String,
        suchanfrage: String = ""
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
            ocrFuerAlleStarten(kategorie)
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

            setText(suchanfrage)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    listeAktualisieren()
                    true
                } else false
            }
        }

        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(suche, LinearLayout.LayoutParams(0, -2, 1f))
            addView(suchLupenButton() { listeAktualisieren() }.apply {
                contentDescription = getString(R.string.home_search_hint)
            }, LinearLayout.LayoutParams(dp(56), -1))
        })

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
                if (importVorbereitungLaeuft) { meldung(getString(R.string.ui_text_032)); return }
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
                    aktiveAnfrageMappeId = null
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
        // Read dimensions first to avoid allocating a full camera-resolution bitmap.
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(daten, 0, daten.size, grenzen)
        require(grenzen.outWidth > 0 && grenzen.outHeight > 0) { "Bild nicht lesbar" }
        var sample = 1
        while (maxOf(grenzen.outWidth, grenzen.outHeight) / sample >= 3200) {
            sample *= 2
        }
        val optionen = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(
            daten, 0, daten.size, optionen
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
        ocrStatusAktualisieren()
        Thread {
            try {
                val text = OcrHilfe.textAusPdf(applicationContext, pdfDaten)
                ocrTextSpeichern(dokumentId, text)
                val anforderungen = if (anfrageMappeId != null) {
                    UnterlagenErkennung.anforderungenErkennen(text)
                } else emptyList()

                runOnUiThread {
                    laufendeImportOcr.remove(dokumentId)
                    startseitenArbeitsstatusAktualisieren()
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
                    ocrStatusAktualisieren()
                    if (entsperrt) meldung(getString(R.string.ui_text_044))
                }
            }
        }.start()
    }

    private fun ocrStatusAktualisieren() {
        startseitenArbeitsstatusAktualisieren()
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
        } else if (importVorbereitungLaeuft || ocrLaeuft || laufendeImportOcr.isNotEmpty()) {
            "Texterkennung läuft im Hintergrund ..."
        } else {
            "$anzahl von ${dokumente.size} Dokumenten für die Textsuche erfasst"
        }

        ocrButton.isEnabled = !importVorbereitungLaeuft && !ocrLaeuft && !ocrIndexLaedt && laufendeImportOcr.isEmpty()
    }

    private fun ocrIndexLaden() {
        val generation = ++ocrGeneration
        ocrIndexLaedt = true
        startseitenArbeitsstatusAktualisieren()

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

    private fun ocrFuerAlleStarten(kategorie: String) {
        if (ocrLaeuft || ocrIndexLaedt) {
            meldung(getString(R.string.ui_text_032))
            return
        }

        val kategoriedokumente = dokumente.filter {
            val zuordnung = kategorieVon(it.first)
            zuordnung != mappenImportKategorie &&
                (kategorie == "Alle Kategorien" || zuordnung == kategorie)
        }
        if (kategoriedokumente.isEmpty()) {
            meldung(getString(R.string.ui_text_038))
            return
        }
        val nochNichtErfasst = kategoriedokumente
            .map { it.first }
            .filter {
                it !in ocrTexte && it !in laufendeImportOcr
            }

        if (nochNichtErfasst.isEmpty()) {
            meldung(
                if (kategoriedokumente.none { it.first in laufendeImportOcr }) "Alle Dokumente wurden bereits erfasst"
                else "Texterkennung läuft im Hintergrund ..."
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_text_033))
            .setMessage(
                (if (nochNichtErfasst.size == 1) "1 Dokument wird" else "${nochNichtErfasst.size} Dokumente werden") +
                " auf diesem Gerät verarbeitet.\n\n" +
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

    private var archivSuchGeneration = 0
    private var archivSuchThread: Thread? = null

    private fun listeAktualisieren() {
        if (!entsperrt || startseiteAktiv || !::dokumentenListe.isInitialized ||
            !::anzahlAnzeige.isInitialized || !::suche.isInitialized) return

        val generation = ++archivSuchGeneration
        archivSuchThread?.interrupt()
        val liste = dokumentenListe
        val suchtext = suche.text.toString().trim()
        val kandidaten = dokumente.filter {
            val kategorie = kategorieVon(it.first)
            kategorie != mappenImportKategorie &&
                (aktiveKategorie == "Alle Kategorien" || kategorie == aktiveKategorie)
        }
        if (suchtext.isEmpty()) {
            archivSuchThread = null
            archivErgebnisAnzeigen(kandidaten)
            return
        }
        val texte = ocrTexte.toMap()
        val sicherheitsGeneration = ocrGeneration
        liste.removeAllViews()
        liste.addView(android.widget.ProgressBar(this))
        anzahlAnzeige.text = getString(R.string.home_search_hint)
        archivSuchThread = Thread {
            val ergebnis = runCatching {
                kandidaten.filter {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    ArchivAnzeige.passt(true, it.second, texte[it.first].orEmpty(), suchtext)
                }
            }
            runOnUiThread {
                if (generation != archivSuchGeneration || sicherheitsGeneration != ocrGeneration ||
                    !entsperrt || !appImVordergrund || startseiteAktiv ||
                    isFinishing || isDestroyed || dokumentenListe !== liste) return@runOnUiThread
                archivSuchThread = null
                ergebnis.onSuccess { archivErgebnisAnzeigen(it) }
                    .onFailure {
                        liste.removeAllViews()
                        anzahlAnzeige.text = getString(R.string.home_search_hint)
                        meldung(getString(R.string.ui_text_045))
                    }
            }
        }.also { it.start() }
    }

    private fun archivErgebnisAnzeigen(sichtbareDokumente: List<Pair<String, String>>) {
        dokumentenListe.removeAllViews()
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
                familienLinkAnzeigen()
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
            } else if (requestCode == PDF_AUSWAHL || requestCode == SCAN_AUSWAHL) {
                aktiveAnfrageMappeId = null
                ausstehendesErgebnis = null
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
        if (resultCode != RESULT_OK && (requestCode == PDF_AUSWAHL || requestCode == SCAN_AUSWAHL)) aktiveAnfrageMappeId = null
        if (!entsperrt || resultCode != RESULT_OK) return

        if (requestCode == SCAN_AUSWAHL) {
            try {
                val ergebnis =
                    GmsDocumentScanningResult
                        .fromActivityResultIntent(data)

                val pdf = ergebnis?.pdf

                if (pdf == null) {
                    aktiveAnfrageMappeId = null
                    meldung(getString(R.string.ui_text_040))
                    return
                }

                val scanMappeId = aktiveAnfrageMappeId
                aktiveAnfrageMappeId = null
                importVorbereitungLaeuft = true
                ocrStatusAktualisieren()
                Thread {
                    try {
                        val daten = contentResolver.openInputStream(pdf.uri)
                            ?.use { it.readBytes() }
                            ?: throw IllegalStateException("Scan konnte nicht gelesen werden")
                        runOnUiThread {
                            importVorbereitungLaeuft = false
                            ocrStatusAktualisieren()
                            if (entsperrt && !isFinishing && !isDestroyed) {
                                aktiveAnfrageMappeId = scanMappeId
                                scanSpeichernDialog(daten)
                            } else if (!isFinishing && !isDestroyed) {
                                aktiveAnfrageMappeId = scanMappeId
                                ausstehendesErgebnis = Triple(requestCode, resultCode, data)
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            importVorbereitungLaeuft = false
                            ocrStatusAktualisieren()
                            if (entsperrt && !isFinishing && !isDestroyed)
                                meldung(getString(R.string.ui_text_045))
                        }
                    }
                }.start()

            } catch (e: Exception) {
                aktiveAnfrageMappeId = null
                meldung(
                    "Scan konnte nicht verarbeitet werden"
                )
            }

            return
        }

        if (requestCode != PDF_AUSWAHL) {
            return
        }

        val uri = data?.data ?: run {
            aktiveAnfrageMappeId = null
            return
        }
        val kategorieFuerImport = importKategorie
        val mappeFuerImport = aktiveAnfrageMappeId
        aktiveAnfrageMappeId = null
        importVorbereitungLaeuft = true
        ocrStatusAktualisieren()
        Thread {
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


            runOnUiThread {
                importVorbereitungLaeuft = false
                ocrStatusAktualisieren()
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!entsperrt) {
                    // Retry from the original URI only after authentication; do not retain plaintext PDF bytes.
                    importKategorie = kategorieFuerImport
                    aktiveAnfrageMappeId = mappeFuerImport
                    ausstehendesErgebnis = Triple(requestCode, resultCode, data)
                    return@runOnUiThread
                }
                try {
            if (mappeFuerImport != null && UnterlagenMappen(this).laden().none { it.id == mappeFuerImport }) {
                meldung(getString(R.string.ui_text_045))
                return@runOnUiThread
            }
            val dokumentId = dokumentVerschluesseln(
                speicherName,
                speicherDaten,
                kategorieFuerImport
            )

            val mappeId = mappeFuerImport
            val anfrageMappeFuerOcr = mappeId
            if (mappeId != null) {
                val verwaltung = UnterlagenMappen(this)
                val mappen = verwaltung.laden()
                val mappe = mappen.find { it.id == mappeId }

                if (mappe != null) {
                    mappe.dokumentIds.add(dokumentId)
                    verwaltung.speichern(mappen)
                }
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
        } catch (e: Exception) {
            runOnUiThread {
                importVorbereitungLaeuft = false
                ocrStatusAktualisieren()
                if (entsperrt && !isFinishing && !isDestroyed) meldung(getString(R.string.ui_text_045))
            }
        }
        }.start()
    }

    // DOKUMENT LÖSCHEN

    private fun privateMappendokumenteBereinigen(ids: List<String>) {
        if (!entsperrt || ocrLaeuft || ocrIndexLaedt) return
        val referenzen = UnterlagenMappen(this).laden().flatMap { it.dokumentIds }.toSet()
        val kandidaten = MappenImportRegeln.loeschbareIds(
            ids, referenzen, laufendeImportOcr.toSet(),
            dokumente.map { it.first }.toSet(), ::kategorieVon
        )
        if (kandidaten.isEmpty()) return
        try {
            val entfernt = kandidaten.filter { id ->
                File(File(filesDir, "dokumente"), id).delete()
            }.toSet()
            if (entfernt.isEmpty()) return
            val uebrige = dokumente.filter { it.first !in entfernt }
            File(filesDir, "dokumentnamen.txt").writeText(
                uebrige.joinToString("\n") { "${it.first}|${it.second}" } +
                    if (uebrige.isNotEmpty()) "\n" else ""
            )
            val editor = einstellungen.edit()
            entfernt.forEach { id ->
                ocrDatei(id).delete()
                ocrTexte.remove(id)
                editor.remove(id)
            }
            editor.apply()
            dokumenteLaden()
            if (startseiteAktiv) startseiteAnzeigen()
            else listeAktualisieren()
        } catch (e: Exception) {
            meldung(getString(R.string.ui_text_047))
        }
    }

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
