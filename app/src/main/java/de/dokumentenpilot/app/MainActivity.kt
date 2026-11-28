
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
    private val schluesselName = FamilienArchivDaten.SCHLUESSEL

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

    private val familienArchivTestVerfuegbar: Boolean
        get() = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun AlertDialog.Builder.familienSicherAnzeigen(): AlertDialog = create().also {
        familienDialog = it
        it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        it.show()
    }

    private var familienLinkAusstehend: String? = null
    @Volatile private var familienPilot: FamilienTestPilot? = null
    @Volatile private var familienEmpfang: FamilienTestPilot? = null
    private var familienSeite = false
    private var familienStatusAnzeige: TextView? = null
    private val familienLetzteVerbindung by lazy { getSharedPreferences("familien_letzte_verbindung", MODE_PRIVATE) }
    private var familienAktuellesArchiv: String? = null
    private var familienSuchtext = ""
    private var familienArchivNavigation: FamilienArchivNavigation? = null
    private var familienGeteiltSeite = false
    private var familienSuchbereich = 0
    private val familienZuletzt = mutableMapOf<String, MutableList<String>>()

    private fun familienPilotGueltig(pilot: FamilienTestPilot) =
        familienPilot === pilot || familienEmpfang === pilot

    private fun familienEmpfangBeenden() {
        familienEmpfang?.close()
        familienEmpfang = null
    }
    private var familienTeilen = false
    private var familienAktivierung: java.util.concurrent.CompletableFuture<Boolean>? = null
    private var familienDialog: AlertDialog? = null
    private var familienArchivAntwort: java.util.concurrent.CompletableFuture<FamilienArchivAustausch.Anfrage?>? = null

    private fun familienAbbrechen() {
        familienEmpfangBeenden()
        familienTeilen = false
        familienAktivierung?.complete(false)
        familienAktivierung = null
        familienArchivAntwort?.complete(null)
        familienArchivAntwort = null
        familienArchivNavigation = null
        familienSuchtext = ""
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
                    .setMessage("Vergleiche beide vollständigen Fingerabdrücke persönlich oder über einen bereits vertrauenswürdigen Kanal. Dein Fingerabdruck muss auf dem anderen Handy als Partner erscheinen und umgekehrt.\n\nDieses Gerät:\n${lokal.chunked(8).joinToString(" ")}\n\nPartnergerät:\n${partner.chunked(8).joinToString(" ")}\n\nNur bei Übereinstimmung bestätigen. Der Archivzugriff ist nur verfügbar, wenn beide Seiten ihn für diese Sitzung ausdrücklich aktivieren.")
                    .setPositiveButton("Bestätigen") { _, _ -> antwort.complete(entsperrt && appImVordergrund && pilot.aktiv()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> antwort.complete(false) }
                    .setOnDismissListener { antwort.complete(false) }
                    .show()
            }
        }
        return try { antwort.get(60, java.util.concurrent.TimeUnit.SECONDS) }
        catch (_: Exception) { false }
    }

    private fun familienPdfRendern(bytes: ByteArray, vorschauen: MutableList<android.graphics.Bitmap>) {
                    val datei = java.io.File.createTempFile("familien-test-", ".pdf", cacheDir)
                    try {
                        datei.writeBytes(bytes)
                        val fd = android.os.ParcelFileDescriptor.open(datei, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        if (!datei.delete()) { fd.close(); error("Testdatei konnte nicht entfernt werden") }
                        val renderer = try { android.graphics.pdf.PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
                        renderer.use {
                            require(it.pageCount == 1)
                            it.openPage(0).use { page ->
                                val bitmap = android.graphics.Bitmap.createBitmap(595, 842, android.graphics.Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    vorschauen.add(bitmap)
                                } catch (e: Exception) { bitmap.recycle(); throw e }
                            }
                        }
                    } finally { datei.delete() }
    }

    private fun familienPdfAnzeigen(vorschauen: MutableList<android.graphics.Bitmap>) {
                        val inhalt = android.widget.LinearLayout(this).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            vorschauen.forEach { bitmap ->
                                addView(android.widget.ImageView(this@MainActivity).apply {
                                    adjustViewBounds = true
                                    setImageBitmap(bitmap)
                                })
                            }
                        }
                        val vorschauDialog = AlertDialog.Builder(this)
                            .setTitle("Empfangene Test-PDFs")
                            .setView(android.widget.ScrollView(this).apply { addView(inhalt) })
                            .setPositiveButton("Schließen", null)
                            .setOnDismissListener { inhalt.removeAllViews(); vorschauen.forEach { it.recycle() }; vorschauen.clear() }
                            .create()
                        vorschauDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        familienDialog = vorschauDialog
                        vorschauDialog.show()
    }

    private fun familienBeitreten(code: String, weitereAufnahme: Boolean = false, mitArchiv: Boolean = false) {
        if (!entsperrt || !appImVordergrund) return
        familienAbbrechen()
        val pilot = FamilienTestPilot(this)
        familienPilot = pilot
        Thread {
            val vorschauen = mutableListOf<android.graphics.Bitmap>()
            var lokalesDokument: String? = null
            val ergebnis = runCatching {
                pilot.wlanUeberwachen(getSystemService(android.net.ConnectivityManager::class.java))
                pilot.beitreten(code, pdfEmpfangen = { _, bytes ->
                    familienPdfRendern(bytes, vorschauen)
                }, weitereAufnahme = weitereAufnahme,
                    archiv = if (mitArchiv && familienArchivTestVerfuegbar) familienArchivAustausch(pilot) { lokalesDokument = it } else null
                ) { lokal, partner -> familienBestaetigen(pilot, lokal, partner) }
            }.getOrNull()
            runOnUiThread {
                if (familienPilot === pilot) {
                    familienAbbrechen()
                    if (mitArchiv && ergebnis != null && entsperrt && appImVordergrund) familienTestAnzeigen()
                    if (entsperrt && appImVordergrund && ergebnis != null && lokalesDokument != null) {
                        vorschauen.forEach { it.recycle() }
                        dokumentOeffnen(checkNotNull(lokalesDokument))
                        return@runOnUiThread
                    }
                    if (entsperrt && appImVordergrund && ergebnis != null && vorschauen.isNotEmpty()) {
                        familienPdfAnzeigen(vorschauen)
                        return@runOnUiThread
                    }
                    if (entsperrt && appImVordergrund) meldung(if (ergebnis != null) "Einladung angenommen. Beide Geräte haben zugestimmt. Testdokument empfangen. Die Geräteidentität ist lokal gespeichert; die Sitzung ist beendet.\n\n$ergebnis" else "Testaustausch nicht abgeschlossen. Eine lokale Bestätigung kann bereits gespeichert sein. Prüfe das WLAN und erstelle bei Bedarf eine neue Einladung.")
                }
                vorschauen.forEach { it.recycle() }
                vorschauen.clear()
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Familieneinladung erhalten")
            .setMessage(if (familienArchivTestVerfuegbar)
                "Nur im selben WLAN. Bei weiteren Familiengeräten auf beiden Seiten die Aufnahme bestätigen."
                else "Nur im selben WLAN. Die Sitzung überträgt synthetische Beispieldokumente.")
            .setPositiveButton(if (familienArchivTestVerfuegbar) "Archiv verbinden" else "Annehmen") { _, _ ->
                familienBeitreten(code, mitArchiv = familienArchivTestVerfuegbar)
            }
            .setNeutralButton("Weiteres Gerät aufnehmen") { _, _ -> familienBeitreten(code, weitereAufnahme = true, mitArchiv = familienArchivTestVerfuegbar) }
        dialog.setNegativeButton(android.R.string.cancel, null)
        familienDialog = dialog.familienSicherAnzeigen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                onBackPressed()
            }
        }
        cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("familien-test-") && it.name.endsWith(".pdf") }?.forEach { it.delete() }
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
        familienSuchtext = ""
        familienArchivNavigation = null
        familienGeteiltSeite = false
        familienZuletzt.clear()
        familienEmpfangBeenden()
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
                if (familienSeite && familienPilot == null && familienDialog == null) {
                    familienTestAnzeigen()
                } else if (startseiteAktiv) {
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
            { entsperrt && appImVordergrund },
            familienfreigabe = if (familienArchivTestVerfuegbar) { id ->
                familienDirektFreigeben(FamilienBerechtigungen.Bereich.MAPPE, listOf(id))
            } else null,
            familienfreigabeVorhanden = { id ->
                (FamilienBerechtigungen.Bereich.MAPPE to id) in familienFreigabeUmfang()
            }
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
        if (!entsperrt || !appImVordergrund) return
        familienSeite = true
        familienGeteiltSeite = false
        startseiteAktiv = true
        val layout = grundLayout()
        val inhalt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(ScrollView(this).apply { addView(inhalt) })
        inhalt.addView(familienKopf("Meine Familie") { onBackPressed() })
        val status = TextView(this).apply {
            setTextColor(sekundaerTextfarbe)
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }
        inhalt.addView(status)
        familienStatusAnzeige = status
        fun karte(symbol: String, titel: String, info: String, oeffnen: () -> Unit) {
            val karte = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(76)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(kartenFarbe)
                    cornerRadius = dp(14).toFloat()
                }
                isFocusable = true
                setOnClickListener { oeffnen() }
            }
            karte.addView(TextView(this).apply {
                text = "$symbol  $titel"
                textSize = 18f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
            })
            karte.addView(TextView(this).apply {
                text = info
                textSize = 14f
                setTextColor(sekundaerTextfarbe)
                setPadding(0, dp(4), 0, 0)
            })
            inhalt.addView(karte, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        karte("▤", "Mein Archiv", "Dokumente ansehen, scannen und importieren") {
            familienAbbrechen()
            familienSeite = false
            dokumentenAnsichtAnzeigen("Alle Kategorien")
        }
        karte("⇩", "Mit mir geteilt", "Archiv eines Familienmitglieds öffnen") {
            familienGeteiltAnzeigen()
        }
        karte("⇧", "Von mir freigegeben", "Rechte und private Ausnahmen verwalten") {
            familienPeerAuswaehlen { peer ->
                familienArchivRegelnAnzeigen(peer)
            }
        }
        karte("♧", "Meine Familienmitglieder", "Gerätenamen und Kopplungen verwalten") {
            familienMehrgeraeteAnzeigen()
        }
        karte("+", "Familienmitglied hinzufügen", "Einladung erstellen oder annehmen") {
            val weitere = FamilienGeraeteVertrauen(this).bestaetigtePeers().isNotEmpty()
            AlertDialog.Builder(this).setTitle("Familienmitglied hinzufügen")
                .setItems(arrayOf("Einladung erstellen", "Einladungslink einfügen")) { _, i ->
                    if (i == 0) familienEinladen(weitereAufnahme = weitere, mitArchiv = true)
                    else familienLinkEingeben()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        karte("⚙", "Einstellungen und Sicherheit", "WLAN-Verfügbarkeit und Gerätesperre") {
            AlertDialog.Builder(this).setTitle("Einstellungen und Sicherheit")
                .setItems(arrayOf("Archiv im WLAN erneut bereitstellen", "Hintergrundzugriff", "Alle Familiengeräte sperren")) { _, i ->
                    when (i) {
                        0 -> familienEmpfangStarten(status)
                        1 -> familienHintergrundAnzeigen()
                        2 -> familienGeraetWiderrufen()
                    }
                }.setNegativeButton("‹ Zurück", null).familienSicherAnzeigen()
        }
        setContentView(layout)
        familienEmpfangStarten(status)
    }

    private fun familienKopf(titel: String, zurueck: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Button(this@MainActivity).apply {
            text = "‹"
            textSize = 26f
            contentDescription = "Zurück"
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { zurueck() }
        }, LinearLayout.LayoutParams(dp(54), -2))
        addView(titelText(titel), LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun familienNamen(): Map<String, String> {
        val store = FamilienGeraeteVertrauen(this)
        val peers = store.bestaetigtePeers().sorted()
        return peers.zip(FamilienArchivNavigation.geraetenamen(peers.map(store::geraeteName))).toMap()
    }

    private fun familienName(peer: String) = familienNamen()[peer] ?: "Familiengerät"

    private fun familienGeteiltAnzeigen() {
        familienGeteiltSeite = true
        val layout = grundLayout()
        val inhalt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply {
            text = "Wähle ein gekoppeltes Familiengerät."
            setTextColor(sekundaerTextfarbe)
        }
        layout.addView(familienKopf("Mit mir geteilt") { onBackPressed() })
        layout.addView(status)
        layout.addView(ScrollView(this).apply { addView(inhalt) })
        val namen = familienNamen()
        if (namen.isEmpty()) status.text = "Noch kein Familiengerät gekoppelt."
        for ((peer, name) in namen) {
            inhalt.addView(dunkelblauerButton(name) { familienPartnerarchivOeffnen(peer, status) })
        }
        setContentView(layout)
    }

    private fun familienHintergrundAnzeigen() {
        if (!familienArchivTestVerfuegbar || !entsperrt || !appImVordergrund) return
        val rechte = FamilienHintergrundRechte(this)
        val an = FamilienHintergrundDienst.laeuft()
        AlertDialog.Builder(this).setTitle("Hintergrundzugriff")
            .setMessage(if (an) "Aktiv · Im privaten WLAN, nur Lesen und Suchen."
                else "Aus · Freigegebene Familiengeräte können bei ausgeschaltetem Bildschirm für bis zu 30 Minuten lesen.")
            .setPositiveButton(if (an) "Beenden" else "Starten") { _, _ ->
                if (!entsperrt || !appImVordergrund) return@setPositiveButton
                if (an) {
                    FamilienHintergrundDienst.beenden(this)
                    familienHintergrundAnzeigen()
                } else if (!rechte.irgendeinGeraet()) {
                    meldung("Wähle zuerst mindestens ein Familiengerät für den Hintergrundzugriff.")
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 109)
                    meldung("Erlaube Benachrichtigungen und starte den Hintergrundzugriff anschließend erneut.")
                } else if (!getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled() ||
                    getSystemService(android.app.NotificationManager::class.java)
                        .getNotificationChannel("familien_archiv_wlan")?.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                    meldung("Aktiviere zuerst die Benachrichtigungen für das Familienarchiv.")
                } else {
                    AlertDialog.Builder(this).setTitle("Archivzugriff bei gesperrtem Bildschirm?")
                        .setMessage("Bis zu 30 Minuten können nur die ausgewählten Geräte ihre bereits freigegebenen PDFs im aktuellen privaten WLAN lesen. Die lokale App bleibt gesperrt.")
                        .setPositiveButton("Zugriff starten") { _, _ ->
                            if (!entsperrt || !appImVordergrund) return@setPositiveButton
                            val gestartet = runCatching {
                                FamilienTestWlan.erfassen(getSystemService(android.net.ConnectivityManager::class.java))
                                FamilienHintergrundDienst.starten(this)
                            }.isSuccess
                            meldung(if (gestartet) "Der Hintergrundzugriff startet. Die Benachrichtigung zeigt den aktiven Dienst."
                                    else "Hintergrundzugriff konnte nicht gestartet werden. Prüfe das private WLAN.")
                        }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                }
            }.setNeutralButton("Geräte auswählen") { _, _ ->
                val namen = familienNamen()
                if (namen.isEmpty()) { meldung("Noch kein Gerät gekoppelt."); return@setNeutralButton }
                val peers = namen.keys.toList()
                val ausgewaehlt = BooleanArray(peers.size) { rechte.erlaubt(peers[it]) }
                AlertDialog.Builder(this).setTitle("Geräte mit Hintergrundzugriff")
                    .setMultiChoiceItems(namen.values.toTypedArray(), ausgewaehlt) { _, i, ok -> ausgewaehlt[i] = ok }
                    .setPositiveButton("Speichern") { _, _ ->
                        if (!entsperrt || !appImVordergrund) return@setPositiveButton
                        FamilienHintergrundDienst.sitzungenBeenden()
                        val gespeichert = peers.mapIndexed { i, peer -> rechte.speichern(peer, ausgewaehlt[i]) }.all { it }
                        FamilienHintergrundDienst.sitzungenBeenden()
                        if (!rechte.irgendeinGeraet()) FamilienHintergrundDienst.beenden(this)
                        if (!gespeichert) meldung("Einige Einstellungen konnten nicht gespeichert werden.")
                        else familienHintergrundAnzeigen()
                    }.setNegativeButton("‹ Zurück") { _, _ -> familienHintergrundAnzeigen() }.familienSicherAnzeigen()
            }.setNegativeButton("‹ Zurück", null).familienSicherAnzeigen()
    }

    private fun familienEmpfangStarten(status: TextView) {
        if (!familienArchivTestVerfuegbar || !entsperrt || !appImVordergrund || !familienSeite) return
        familienEmpfangBeenden()
        if (FamilienGeraeteVertrauen(this).bestaetigtePeers().isEmpty()) {
            status.text = "Noch kein Gerät gekoppelt"
            return
        }
        val pilot = FamilienTestPilot(this)
        familienEmpfang = pilot
        status.text = "Archiv wird im WLAN bereitgestellt …"
        Thread {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            val netz = runCatching { FamilienTestWlan.erfassen(cm) }.getOrNull()
            val bereit = netz != null && runCatching {
                pilot.wlanUeberwachen(cm)
                val archiv = familienArchivAustausch(pilot) {}
                runOnUiThread {
                    if (familienEmpfang === pilot) status.text = "Gekoppelte Geräte · Archiv im WLAN verfügbar"
                }
                pilot.archivBereitstellen(getSystemService(android.net.nsd.NsdManager::class.java), archiv) { peer ->
                    familienLetzteVerbindung.edit().putLong(peer, System.currentTimeMillis()).apply()
                }
            }.isSuccess
            pilot.close()
            runOnUiThread {
                if (familienEmpfang === pilot) {
                    familienEmpfang = null
                    if (bereit && netz?.gueltig() == true && entsperrt && familienSeite && appImVordergrund) {
                        familienEmpfangStarten(status)
                    } else {
                        status.text = "Archiv im WLAN nicht verfügbar · unter Sicherheit erneut starten"
                    }
                }
            }
        }.start()
    }

    private fun familienPartnerarchivOeffnen(peer: String, status: TextView, wiederholung: Boolean = false) {
        if (!familienArchivTestVerfuegbar || !entsperrt || !appImVordergrund || familienPilot != null) return
        val pilot = FamilienTestPilot(this)
        familienPilot = pilot
        familienArchivNavigation = FamilienArchivNavigation()
        familienSuchtext = ""
        familienSuchbereich = 0
        val name = familienName(peer)
        status.text = "$name · Verbindung wird hergestellt …"
        var lokalesDokument: String? = null
        Thread {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            val netz = runCatching { FamilienTestWlan.erfassen(cm) }.getOrNull()
            val verbunden = netz != null && runCatching {
                pilot.wlanUeberwachen(cm)
                pilot.archivOeffnen(getSystemService(android.net.nsd.NsdManager::class.java), peer,
                    familienArchivAustausch(pilot, wiederverbinden = true) { lokalesDokument = it }) { geraet ->
                        familienLetzteVerbindung.edit().putLong(geraet, System.currentTimeMillis()).apply()
                        runOnUiThread { if (familienPilot === pilot) status.text = "$name · Verbunden" }
                    }
            }.getOrDefault(false)
            pilot.close()
            runOnUiThread {
                if (familienPilot !== pilot) return@runOnUiThread
                familienPilot = null
                familienArchivNavigation = null
                familienSuchtext = ""
                if (!entsperrt || !appImVordergrund) return@runOnUiThread
                if (!verbunden && !wiederholung && familienGeteiltSeite && netz?.gueltig() == true) {
                    familienPartnerarchivOeffnen(peer, status, wiederholung = true)
                    return@runOnUiThread
                }
                status.text = if (verbunden) "$name · Gekoppelt"
                    else "$name momentan nicht erreichbar · WLAN und Archivfreigabe prüfen und erneut antippen"
                lokalesDokument?.let {
                    familienEmpfangBeenden()
                    familienSeite = false
                    dokumentOeffnen(it)
                }
            }
        }.start()
    }

    private fun familienArchivKatalog(owner: String) =
        FamilienArchivDaten.katalog(this, owner, dokumente, ::kategorieVon)

    private data class FamilienArchivPdf(val titel: String, val renderer: android.graphics.pdf.PdfRenderer)

    private fun familienArchivAustausch(
        pilot: FamilienTestPilot, wiederverbinden: Boolean = false, lokalOeffnen: (String) -> Unit
    ): FamilienArchivAustausch {
        val owner = FamilienGeraeteIdentitaet.laden().fingerprint
        val store = FamilienArchivFreigaben(this)
        val trust = FamilienGeraeteVertrauen(this)
        fun katalog(): Pair<List<FamilienArchivDaten.Eintrag>, List<String>> {
            val antwort = java.util.concurrent.CompletableFuture<Pair<List<FamilienArchivDaten.Eintrag>, List<String>>>()
            runOnUiThread {
                if (!familienPilotGueltig(pilot) || !entsperrt || !appImVordergrund || !pilot.aktiv()) {
                    antwort.complete(emptyList<FamilienArchivDaten.Eintrag>() to emptyList())
                } else {
                    runCatching { familienArchivKatalog(owner) to kategorien.filter { it != mappenImportKategorie } }
                        .onSuccess { antwort.complete(it) }
                        .onFailure { antwort.completeExceptionally(it) }
                }
            }
            return antwort.get(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        fun zugriff(peer: String) = FamilienArchivZugriff(owner,
            bestaetigt = { trust.status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT },
            aktiv = pilot::aktiv,
            katalog = { katalog().first.map { it.dokument } },
            freigaben = { store.lesen(owner, peer)?.freigaben.orEmpty() },
            privat = { store.lesen(owner, peer)?.privat.orEmpty() },
            lesen = {
                require(FamilienArchivAustausch.gueltig(it))
                val datei = File(filesDir, "dokumente/$it")
                require(datei.length() in 1L..(FamilienArchivAustausch.MAX_PDF + 64L))
                dokumentEntschluesseln(it)
            })
        fun aendern(
            peer: String, id: String,
            anwenden: (FamilienArchivDaten.Eintrag, FamilienArchivFreigaben.Regeln) -> Boolean
        ): Boolean {
            val auftrag = java.util.concurrent.FutureTask {
                if (!entsperrt || !appImVordergrund || !familienPilotGueltig(pilot) || !pilot.aktiv()) {
                    return@FutureTask false
                }
                val eintrag = familienArchivKatalog(owner).singleOrNull { it.dokument.id == id }
                    ?: return@FutureTask false
                val regeln = store.lesen(owner, peer) ?: return@FutureTask false
                if (id in regeln.privat || !pilot.aktiv()) return@FutureTask false
                if (!anwenden(eintrag, regeln)) return@FutureTask false
                listeAktualisieren()
                true
            }
            runOnUiThread(auftrag)
            return try {
                auftrag.get()
            } catch (_: java.util.concurrent.ExecutionException) {
                false
            }
        }
        fun importErlaubt(peer: String, ziel: String, kategorien: List<String>): Boolean {
            val regeln = store.lesen(owner, peer) ?: return false
            return ziel in kategorien && ziel != mappenImportKategorie &&
                FamilienArchivRegeln.darfEinreichen(owner, peer, ziel, regeln.freigaben)
        }
        return FamilienArchivAustausch(
            katalog = { peer ->
                val regeln = store.lesen(owner, peer)
                val (eintraege, kategorien) = katalog()
                if (regeln == null) emptyList()
                else FamilienArchivDaten.neuesAngebot(peer, eintraege, kategorien, regeln, bearbeiten = true)
            },
            lesen = { peer, id, weitergeben -> zugriff(peer).ansehen(peer, id, weitergeben) },
            darfAnsehen = { peer, id -> zugriff(peer).darfAnsehen(peer, id) },
            auswaehlen = { peer, eintraege, importZiele, verbunden ->
                familienArchivDokumentWaehlen(pilot, peer, eintraege, importZiele, lokalOeffnen, verbunden, wiederverbinden)
            },
            einreichZiele = { peer -> katalog().second.let { liste ->
                liste.filter { importErlaubt(peer, it, liste) }
            } },
            darfEinreichen = { peer, ziel -> importErlaubt(peer, ziel, katalog().second) },
            lokalePdf = { id ->
                val eintrag = katalog().first.singleOrNull { it.dokument.id == id }
                val datei = File(filesDir, "dokumente/$id")
                if (eintrag == null || !pilot.aktiv() ||
                    !FamilienArchivAustausch.gueltigerTitel(eintrag.titel) ||
                    datei.length() !in 1L..(FamilienArchivAustausch.MAX_PDF + 64L)) null
                else FamilienArchivAustausch.LokalePdf(eintrag.titel, dokumentEntschluesseln(id))
            },
            pdfEinreichen = { peer, ziel, titel, bytes ->
                runCatching {
                    familienPdfRenderer(bytes).use { check(it.pageCount > 0) }
                    val importBytes = bytes.copyOf()
                    val auftrag = java.util.concurrent.FutureTask {
                        try {
                            if (!familienPilotGueltig(pilot) || !entsperrt || !appImVordergrund || !pilot.aktiv() ||
                                !importErlaubt(peer, ziel, kategorien)) return@FutureTask false
                            val id = familienPdfSpeichern(peer, pdfDateiname(titel), importBytes, ziel)
                            if (id != null) {
                                listeAktualisieren()
                                importOcrStarten(id, importBytes.copyOf(), null, pufferFreigeben = true)
                            }
                            true
                        } finally {
                            importBytes.fill(0)
                        }
                    }
                    runOnUiThread(auftrag)
                    auftrag.get()
                }.getOrDefault(false)
            },
            umbenennen = { peer, id, alterTitel, titel ->
                aendern(peer, id) { eintrag, regeln ->
                    val doc = eintrag.dokument
                    if (eintrag.titel != alterTitel || !FamilienArchivAustausch.gueltigerTitel(titel) ||
                        !FamilienBerechtigungen.erlaubt(peer, doc, FamilienBerechtigungen.Aktion.ANSEHEN, regeln.freigaben) ||
                        !FamilienBerechtigungen.erlaubt(peer, doc, FamilienBerechtigungen.Aktion.UMBENENNEN, regeln.freigaben) ||
                        !pilot.aktiv()) return@aendern false
                    dokumentNamenSpeichern(id, pdfDateiname(titel))
                    true
                }
            },
            verschieben = { peer, id, quelle, ziel ->
                aendern(peer, id) { eintrag, regeln ->
                    val doc = eintrag.dokument
                    if (quelle != doc.kategorie || ziel == mappenImportKategorie || ziel !in kategorien ||
                        !FamilienArchivRegeln.darfVerschieben(peer, doc, ziel, regeln.freigaben) ||
                        !pilot.aktiv()) return@aendern false
                    val bisher = einstellungen.getString(id, null)
                    if (einstellungen.edit().putString(id, ziel).commit()) {
                        true
                    } else {
                        einstellungen.edit().putString(id, bisher).commit()
                        false
                    }
                }
            },
            loeschen = { peer, angeboten ->
                val antwort = java.util.concurrent.CompletableFuture<Boolean>()
                val frist = android.os.SystemClock.elapsedRealtime() + 30_000
                val auftrag = Runnable {
                    fun aktuellerEintrag(): FamilienArchivDaten.Eintrag? {
                        if (!entsperrt || !appImVordergrund || !familienPilotGueltig(pilot) || !pilot.aktiv() ||
                            android.os.SystemClock.elapsedRealtime() >= frist || antwort.isDone) return null
                        val eintrag = familienArchivKatalog(owner).singleOrNull { it.dokument.id == angeboten.id }
                            ?: return null
                        val doc = eintrag.dokument
                        val regeln = store.lesen(owner, peer) ?: return null
                        return eintrag.takeIf {
                            eintrag.titel == angeboten.titel && doc.kategorie == angeboten.quellKategorie &&
                                doc.id !in regeln.privat && !ocrLaeuft && doc.id !in laufendeImportOcr &&
                                FamilienBerechtigungen.erlaubt(peer, doc, FamilienBerechtigungen.Aktion.ANSEHEN, regeln.freigaben) &&
                                FamilienBerechtigungen.erlaubt(peer, doc, FamilienBerechtigungen.Aktion.LOESCHEN, regeln.freigaben)
                        }
                    }
                    val eintrag = runCatching { aktuellerEintrag() }.getOrNull()
                    if (eintrag == null) {
                        antwort.complete(false)
                        return@Runnable
                    }
                    val name = familienName(peer)
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Löschen im eigenen Archiv bestätigen")
                        .setMessage("$name möchte „${eintrag.titel}“ dauerhaft aus deinem Archiv löschen.")
                        .setPositiveButton("Dauerhaft löschen") { _, _ ->
                            val geloescht = runCatching {
                                val aktuell = aktuellerEintrag() ?: return@runCatching false
                                pilot.aktiv() && dokumentEntfernen(aktuell.dokument.id)
                            }.getOrDefault(false)
                            antwort.complete(geloescht)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .setOnDismissListener { antwort.complete(false) }
                        .familienSicherAnzeigen()
                    antwort.whenComplete { _, _ -> runOnUiThread { dialog.dismiss() } }
                }
                runOnUiThread(auftrag)
                try {
                    antwort.get(35, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: java.util.concurrent.TimeoutException) {
                    antwort.complete(false)
                    false
                }
            },
            aenderungGemeldet = { gespeichert ->
                runOnUiThread {
                    if (familienPilotGueltig(pilot) && entsperrt && appImVordergrund) {
                        meldung(if (gespeichert) "Änderung im Partnerarchiv gespeichert." else "Das Partnerarchiv hat die Änderung abgelehnt.")
                    }
                }
            },
            empfangen = { eintrag, bytes, verlaengern ->
                if (wiederverbinden) runOnUiThread {
                    val peer = familienAktuellesArchiv
                    if (familienPilot === pilot && peer != null) {
                        val liste = familienZuletzt.getOrPut(peer) { mutableListOf() }
                        liste.remove(eintrag.id)
                        liste.add(0, eintrag.id)
                        if (liste.size > 50) liste.removeAt(liste.lastIndex)
                    }
                }
                val renderer = familienPdfRenderer(bytes)
                bytes.fill(0)
                familienArchivVorschau(pilot, FamilienArchivPdf(eintrag.titel, renderer), verlaengern)
            })
    }

    private fun familienPdfRenderer(bytes: ByteArray): android.graphics.pdf.PdfRenderer {
        val datei = File.createTempFile("familien-test-", ".pdf", cacheDir)
        return try {
            val fd = android.os.ParcelFileDescriptor.open(datei, android.os.ParcelFileDescriptor.MODE_READ_WRITE)
            try {
                check(datei.delete()) { "Temporäre PDF konnte nicht entkoppelt werden" }
                val schreibFd = android.os.ParcelFileDescriptor.dup(fd.fileDescriptor)
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(schreibFd).use { it.write(bytes) }
                android.system.Os.lseek(fd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
                android.graphics.pdf.PdfRenderer(fd)
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        } finally {
            datei.delete()
        }
    }

    private fun familienPdfSpeichern(peer: String, titel: String, bytes: ByteArray, kategorie: String): String? {
        val id = FamilienArchivAustausch.einreichId(peer, kategorie, bytes)
        val ordner = File(filesDir, "dokumente")
        if (!ordner.isDirectory) check(ordner.mkdirs())
        val staging = File(ordner, "$id.einreichung")
        val datei = File(ordner, id)
        if (dokumente.any { it.first == id }) {
            dokumenteLaden()
            check(datei.isFile)
            return null
        }
        check(!datei.exists())
        try {
            val verschluesselt = datenVerschluesseln(bytes)
            java.io.FileOutputStream(staging).use {
                it.write(verschluesselt)
                it.fd.sync()
            }
            check(einstellungen.edit().putString(id, kategorie).commit())
            dokumentIndexSpeichern(dokumente + (id to titel))
        } catch (e: Exception) {
            staging.delete()
            einstellungen.edit().remove(id).commit()
            throw e
        }
        // If the process stops before the rename, index loading completes the move.
        dokumenteLaden()
        check(datei.isFile)
        return id
    }

    private fun familienArchivDokumentWaehlen(
        pilot: FamilienTestPilot, peer: String, eintraege: List<FamilienArchivAustausch.Eintrag>,
        importZiele: List<String>, lokalOeffnen: (String) -> Unit, verbunden: () -> Boolean, wiederverbinden: Boolean
    ): FamilienArchivAustausch.Anfrage? {
        val antwort = java.util.concurrent.CompletableFuture<FamilienArchivAustausch.Anfrage?>()
        var auswahlDialog: AlertDialog? = null
        runOnUiThread {
            if (!entsperrt || !appImVordergrund || familienPilot !== pilot || !pilot.aktiv()) {
                antwort.complete(null)
                return@runOnUiThread
            }
            familienArchivAntwort = antwort
            if (wiederverbinden) {
                familienAktuellesArchiv = peer
            }
            val name = familienName(peer)
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val suche = EditText(this).apply {
                hint = "Dokumente suchen"
                if (wiederverbinden) setText(familienSuchtext)
                setSingleLine(true)
                filters = arrayOf(android.text.InputFilter.LengthFilter(250))
            }
            val assistent = Button(this).apply { text = "Assistent fragen" }
            val lokale = dokumente.filter { kategorieVon(it.first) != mappenImportKategorie }
            val texte = ocrTexte.toMap()
            val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            layout.addView(suche)
            layout.addView(assistent)
            val suchbereich = Button(this)
            val sortieren = Button(this).apply { text = "A–Z" }
            val scroll = ScrollView(this).apply { addView(liste) }
            val navi = if (wiederverbinden) familienArchivNavigation ?: FamilienArchivNavigation() else FamilienArchivNavigation()
            lateinit var anzeigen: () -> Unit
            lateinit var dialog: AlertDialog
            fun zurueck() {
                if (wiederverbinden && navi.zurueck()) anzeigen()
                else dialog.dismiss()
            }
            val kopf = familienKopf(name, ::zurueck)
            layout.addView(kopf, 0)
            val werkzeuge = LinearLayout(this).apply {
                addView(suchbereich, LinearLayout.LayoutParams(0, -2, 1f))
                addView(sortieren, LinearLayout.LayoutParams(dp(70), -2))
            }
            if (wiederverbinden) layout.addView(werkzeuge)
            layout.addView(scroll, LinearLayout.LayoutParams(-1, dp(minOf(380, (resources.configuration.screenHeightDp - 245).coerceAtLeast(90)))))
            dialog = AlertDialog.Builder(this).setView(layout)
                .setOnDismissListener { antwort.complete(null) }.create()
            if (Build.VERSION.SDK_INT >= 33) {
                dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                    zurueck()
                }
            }
            dialog.setOnKeyListener { _, key, event ->
                if (key == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    zurueck()
                    true
                } else false
            }
            auswahlDialog = dialog
            if (importZiele.isNotEmpty() && lokale.isNotEmpty()) {
                val lokalArchiv = lokale.filter { FamilienArchivAustausch.gueltigerTitel(it.second) }.take(100)
                val einreichen = Button(this).apply { text = "PDF ins Partnerarchiv einreichen" }
                einreichen.isEnabled = lokalArchiv.isNotEmpty()
                layout.addView(einreichen, layout.childCount - 1)
                fun auswahl(titel: String, eintraege: List<String>, gewaehlt: (Int) -> Unit) {
                    val editor = AlertDialog.Builder(this).setTitle(titel)
                        .setItems(eintraege.toTypedArray()) { _, index -> gewaehlt(index) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                    editor.setOnDismissListener {
                        if (!antwort.isDone && familienDialog === editor) familienDialog = dialog
                    }
                    antwort.whenComplete { _, _ ->
                        runOnUiThread {
                            editor.dismiss()
                            dialog.dismiss()
                        }
                    }
                    familienDialog = editor
                    editor.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    editor.show()
                }
                einreichen.setOnClickListener {
                    auswahl("Freigegebene Zielkategorie", importZiele) { ziel ->
                        auswahl("Eigenes PDF zum Einreichen", lokalArchiv.map { it.second }) { wahl ->
                            if (familienPilot === pilot && entsperrt && appImVordergrund && pilot.aktiv()) {
                                antwort.complete(FamilienArchivAustausch.Anfrage(lokalArchiv[wahl].first,
                                    einreichKategorie = importZiele[ziel]))
                            }
                        }
                    }
                }
            }

            fun weiterhinAktiv() = !antwort.isDone && entsperrt && appImVordergrund &&
                familienPilot === pilot && pilot.aktiv()
            fun ausfuehren(anfrage: FamilienArchivAustausch.Anfrage) {
                if (!weiterhinAktiv()) return
                navi.merken(scroll.scrollY)
                antwort.complete(anfrage)
                dialog.dismiss()
            }
            fun editor(builder: AlertDialog.Builder): AlertDialog {
                val fenster = builder.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                fenster.setOnDismissListener {
                    if (!antwort.isDone && familienDialog === fenster) familienDialog = dialog
                }
                antwort.whenComplete { _, _ -> runOnUiThread { fenster.dismiss() } }
                return fenster
            }
            fun dokumentAktion(eintrag: FamilienArchivAustausch.Eintrag, aktion: String) {
                if (!weiterhinAktiv()) return
                when (aktion) {
                    "Öffnen" -> ausfuehren(FamilienArchivAustausch.Anfrage(eintrag.id))
                    "Umbenennen" -> {
                        val titel = EditText(this).apply {
                            setSingleLine(true)
                            setText(eintrag.titel)
                            filters = arrayOf(android.text.InputFilter.LengthFilter(200))
                        }
                        val eingabe = editor(AlertDialog.Builder(this).setTitle("Dokument umbenennen")
                            .setView(titel).setPositiveButton("Speichern", null))
                        eingabe.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val neu = titel.text.toString().trim()
                            if (!FamilienArchivAustausch.gueltigerTitel(neu)) titel.error = "Bitte gültigen Namen eingeben"
                            else ausfuehren(FamilienArchivAustausch.Anfrage(eintrag.id, neu))
                        }
                    }
                    "Verschieben" -> editor(AlertDialog.Builder(this).setTitle("Zielkategorie wählen")
                        .setItems(eintrag.zielKategorien.toTypedArray()) { _, index ->
                            ausfuehren(FamilienArchivAustausch.Anfrage(eintrag.id, zielKategorie = eintrag.zielKategorien[index]))
                        })
                    "Dokumenteninformationen" -> editor(AlertDialog.Builder(this).setTitle(eintrag.titel)
                        .setMessage((listOf("Eigentümer: $name") +
                            listOfNotNull(eintrag.quellKategorie.takeIf { it.isNotBlank() }?.let { "Kategorie: $it" }) +
                            eintrag.mappen.map { "Mappe: $it" }).joinToString("\n")))
                    "Löschung anfragen" -> editor(AlertDialog.Builder(this).setTitle("Löschung anfragen?")
                        .setMessage("Der Eigentümer muss die Löschung von „${eintrag.titel}“ auf seinem Gerät bestätigen.")
                        .setPositiveButton("Anfrage senden") { _, _ ->
                            ausfuehren(FamilienArchivAustausch.Anfrage(eintrag.id, loeschen = true))
                        })
                }
            }
            fun button(text: String, aktion: () -> Unit) {
                liste.addView(dunkelblauerButton(text) { if (weiterhinAktiv()) aktion() })
            }
            fun dokumenteAnzeigen(remote: List<FamilienArchivAustausch.Eintrag>, eigene: List<Pair<String, String>>) {
                if (remote.isEmpty() && eigene.isEmpty()) {
                    liste.addView(TextView(this).apply {
                        text = "Keine passenden freigegebenen Dokumente"
                        setTextColor(sekundaerTextfarbe)
                    })
                }
                for (eintrag in remote.distinctBy { it.id }) {
                    val zeile = LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = hintergrund(kartenFarbe, 12f)
                        setPadding(dp(10), dp(4), dp(4), dp(4))
                    }
                    val beschriftung = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        minimumHeight = dp(48)
                        gravity = Gravity.CENTER_VERTICAL
                        isFocusable = true
                        setOnClickListener { dokumentAktion(eintrag, "Öffnen") }
                        addView(TextView(this@MainActivity).apply {
                            text = eintrag.titel
                            textSize = 16f
                            setTextColor(dunkelblau)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = name
                            setTextColor(sekundaerTextfarbe)
                        })
                    }
                    zeile.addView(beschriftung, LinearLayout.LayoutParams(0, -2, 1f))
                    val menu = Button(this).apply {
                        text = "⋮"
                        textSize = 23f
                        contentDescription = "Aktionen für ${eintrag.titel}"
                        minimumWidth = dp(48)
                        minimumHeight = dp(48)
                        setOnClickListener {
                            if (!weiterhinAktiv()) return@setOnClickListener
                            val aktionen = FamilienArchivNavigation.aktionen(eintrag)
                            editor(AlertDialog.Builder(this@MainActivity).setTitle(eintrag.titel)
                                .setItems(aktionen.toTypedArray()) { _, index -> dokumentAktion(eintrag, aktionen[index]) })
                        }
                    }
                    zeile.addView(menu, LinearLayout.LayoutParams(dp(54), -2))
                    liste.addView(zeile, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                }
                for ((id, titel) in eigene.take(100)) {
                    button(titel + "\nMein Archiv") {
                        lokalOeffnen(id)
                        antwort.complete(null)
                        dialog.dismiss()
                    }
                }
            }

            val bereiche = listOf("Mit mir geteilt", "Mein Archiv", "Alle berechtigten Dokumente")
            val gruppierteMappen = FamilienArchivNavigation.gruppieren(eintraege, mappen = true)
            val gruppierteKategorien = FamilienArchivNavigation.gruppieren(eintraege, mappen = false)
            val archiv = FamilienArchivNavigation.Bereich.ARCHIV
            val mappen = FamilienArchivNavigation.Bereich.MAPPEN
            val kategorien = FamilienArchivNavigation.Bereich.KATEGORIEN
            fun oeffnen(bereich: FamilienArchivNavigation.Bereich, gruppenname: String = "") {
                navi.oeffnen(FamilienArchivNavigation.Seite(bereich, gruppenname), scroll.scrollY)
                anzeigen()
            }
            fun gefiltert() = when (navi.seite.bereich) {
                FamilienArchivNavigation.Bereich.MAPPE -> gruppierteMappen[navi.seite.name].orEmpty()
                FamilienArchivNavigation.Bereich.KATEGORIE -> gruppierteKategorien[navi.seite.name].orEmpty()
                FamilienArchivNavigation.Bereich.ZULETZT -> familienZuletzt[peer].orEmpty().mapNotNull { id -> eintraege.find { it.id == id } }
                else -> eintraege
            }
            fun lokaleSucheErlaubt() = !wiederverbinden || navi.seite.bereich == FamilienArchivNavigation.Bereich.ALLE
            fun aktualisieren(scrollAnwenden: Boolean = false) {
                val query = suche.text.toString().trim()
                if (wiederverbinden) familienSuchtext = suche.text.toString()
                val seite = navi.seite
                val dokumentansicht = !wiederverbinden || seite.bereich !in setOf(archiv, mappen, kategorien)
                suche.visibility = if (dokumentansicht) View.VISIBLE else View.GONE
                assistent.visibility = suche.visibility
                werkzeuge.visibility = if (dokumentansicht && wiederverbinden) View.VISIBLE else View.GONE
                suchbereich.visibility = if (lokaleSucheErlaubt()) View.VISIBLE else View.INVISIBLE
                sortieren.text = if (navi.titelAufsteigend) "A–Z" else "Z–A"
                sortieren.visibility = if (seite.bereich == FamilienArchivNavigation.Bereich.ZULETZT) View.GONE else View.VISIBLE
                suchbereich.text = bereiche[familienSuchbereich]
                (kopf.getChildAt(1) as TextView).text = when {
                    seite.name.isNotBlank() -> seite.name
                    seite.bereich == mappen -> "Mappen"
                    seite.bereich == kategorien -> "Kategorien"
                    seite.bereich == FamilienArchivNavigation.Bereich.ALLE -> "Freigegebene Dokumente"
                    seite.bereich == FamilienArchivNavigation.Bereich.ZULETZT -> "Zuletzt geöffnet"
                    else -> name
                }
                liste.removeAllViews()
                when {
                    dokumentansicht -> {
                        val passend = gefiltert().filter { query.isEmpty() || ArchivAnzeige.passt(it.suchbar, it.titel, "", query) }
                        val sortiert = if (seite.bereich == FamilienArchivNavigation.Bereich.ZULETZT) passend else {
                            val nachTitel = passend.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.titel })
                            if (navi.titelAufsteigend) nachTitel else nachTitel.asReversed()
                        }
                        val eigene = lokale.filter { (id, titel) -> ArchivAnzeige.passt(true, titel, texte[id].orEmpty(), query) }
                        dokumenteAnzeigen(sortiert.takeUnless { wiederverbinden && lokaleSucheErlaubt() && familienSuchbereich == 1 }.orEmpty(),
                            eigene.takeIf { if (wiederverbinden) lokaleSucheErlaubt() && familienSuchbereich != 0 else query.isNotEmpty() }.orEmpty())
                    }
                    seite.bereich == archiv -> {
                        button("Alle freigegebenen Dokumente (${eintraege.size})") { oeffnen(FamilienArchivNavigation.Bereich.ALLE) }
                        button("▣  Mappen (${gruppierteMappen.size})") { oeffnen(mappen) }
                        button("▤  Kategorien (${gruppierteKategorien.size})") { oeffnen(kategorien) }
                        button("Zuletzt geöffnet") { oeffnen(FamilienArchivNavigation.Bereich.ZULETZT) }
                    }
                    else -> {
                        val gruppen = if (seite.bereich == mappen) gruppierteMappen else gruppierteKategorien
                        if (gruppen.isEmpty()) liste.addView(TextView(this).apply {
                            text = if (seite.bereich == mappen) "Keine freigegebenen Mappen" else "Keine freigegebenen Kategorien"
                            setTextColor(sekundaerTextfarbe)
                        })
                        for ((gruppe, docs) in gruppen) {
                            button("$gruppe (${docs.size})") {
                                oeffnen(if (seite.bereich == mappen) FamilienArchivNavigation.Bereich.MAPPE
                                        else FamilienArchivNavigation.Bereich.KATEGORIE, gruppe)
                            }
                        }
                    }
                }
                if (scrollAnwenden) scroll.post { scroll.scrollTo(0, navi.scrollposition) }
            }
            anzeigen = { aktualisieren(scrollAnwenden = true) }
            sortieren.setOnClickListener {
                if (weiterhinAktiv()) {
                    navi.titelAufsteigend = !navi.titelAufsteigend
                    aktualisieren()
                }
            }
            suchbereich.setOnClickListener {
                editor(AlertDialog.Builder(this).setTitle("Suchbereich").setItems(bereiche.toTypedArray()) { _, index ->
                    if (weiterhinAktiv()) {
                        familienSuchbereich = index
                        aktualisieren()
                    }
                })
            }
            assistent.setOnClickListener {
                val anfrage = AssistentErkennung.erkennen(suche.text.toString()) ?: return@setOnClickListener
                val remote = gefiltert().filter { it.assistent && (!wiederverbinden || !lokaleSucheErlaubt() || familienSuchbereich != 1) }
                    .associateBy { "remote:${it.id}" }
                val eigene = lokale.takeUnless { wiederverbinden && (!lokaleSucheErlaubt() || familienSuchbereich == 0) }.orEmpty()
                val kandidaten = eigene + remote.map { (id, eintrag) -> id to eintrag.titel }
                val treffer = AssistentSuche.assistentTreffer(kandidaten, texte, anfrage)
                liste.removeAllViews()
                dokumenteAnzeigen(treffer.mapNotNull { remote[it.first] }, treffer.filterNot { it.first in remote })
            }
            suche.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = aktualisieren()
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            anzeigen()
            familienDialog = dialog
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.show()
        }
        try {
            repeat(if (wiederverbinden) 300 else 45) {
                try {
                    return antwort.get(1, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: java.util.concurrent.TimeoutException) {
                    check(verbunden())
                }
            }
            return null
        } finally {
            runOnUiThread {
                if (familienArchivAntwort === antwort) {
                    familienArchivAntwort = null
                    familienDialog?.dismiss()
                    auswahlDialog?.dismiss()
                    familienDialog = null
                }
            }
        }
    }

    private fun familienArchivVorschau(
        pilot: FamilienTestPilot, pdf: FamilienArchivPdf, verlaengern: () -> Boolean
    ) {
        val geschlossen = java.util.concurrent.CountDownLatch(1)
        runOnUiThread {
            if (familienPilot === pilot && entsperrt && appImVordergrund && pilot.aktiv() && geschlossen.count != 0L) {
                familienArchivPdfAnzeigen(pdf) { geschlossen.countDown() }
            } else {
                pdf.renderer.close()
                geschlossen.countDown()
            }
        }
        try {
            repeat(90) {
                if (geschlossen.await(1, java.util.concurrent.TimeUnit.SECONDS)) return
                check(verlaengern())
            }
        } finally {
            if (geschlossen.count != 0L) {
                geschlossen.countDown()
                runOnUiThread { if (familienPilot === pilot) familienDialog?.dismiss() }
            }
        }
    }

    private fun familienArchivPdfAnzeigen(pdf: FamilienArchivPdf, geschlossen: () -> Unit) {
        val bild = android.widget.ImageView(this).apply { adjustViewBounds = true }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val nav = LinearLayout(this)
        val zurueck = Button(this)
        val weiter = Button(this)
        val status = TextView(this)
        zurueck.text = "Zurück"
        weiter.text = "Weiter"
        nav.addView(zurueck)
        nav.addView(status)
        nav.addView(weiter)
        layout.addView(nav)
        layout.addView(ScrollView(this).apply { addView(bild) })
        var bitmap: android.graphics.Bitmap? = null
        var index = 0
        val dialog = AlertDialog.Builder(this).setView(layout)
            .setPositiveButton("Schließen", null).setOnDismissListener {
                bild.setImageDrawable(null)
                bitmap?.recycle()
                pdf.renderer.close()
                geschlossen()
            }.create()
        layout.addView(familienKopf(pdf.titel) { dialog.dismiss() }, 0)
        fun anzeigen() {
            runCatching {
                pdf.renderer.openPage(index).use { page ->
                    val breite = 900
                    val hoehe = (breite.toLong() * page.height / page.width).coerceIn(1L, 1800L).toInt()
                    val neu = android.graphics.Bitmap.createBitmap(breite, hoehe, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        neu.eraseColor(Color.WHITE)
                        page.render(neu, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bild.setImageBitmap(neu)
                        bitmap?.recycle()
                        bitmap = neu
                    } catch (e: Exception) {
                        neu.recycle()
                        throw e
                    }
                }
                status.text = "${index + 1} / ${pdf.renderer.pageCount}"
                zurueck.isEnabled = index > 0
                weiter.isEnabled = index + 1 < pdf.renderer.pageCount
            }.onFailure { dialog.dismiss(); meldung("PDF konnte nicht angezeigt werden.") }
        }
        zurueck.setOnClickListener { index--; anzeigen() }
        weiter.setOnClickListener { index++; anzeigen() }
        familienDialog = dialog
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        anzeigen()
    }

    private fun familienArchivRegelnAnzeigen(
        peer: String, direkt: List<Pair<FamilienBerechtigungen.Bereich, String>> = emptyList(),
        weitereEmpfaenger: List<String> = emptyList()
    ) {
        if (!entsperrt || !appImVordergrund || !familienArchivTestVerfuegbar) return
        val owner = runCatching { FamilienGeraeteIdentitaet.laden().fingerprint }.getOrNull()
        val store = FamilienArchivFreigaben(this)
        val regeln = owner?.let { store.lesen(it, peer) }
        val katalog = owner?.let { runCatching { familienArchivKatalog(it) }.getOrNull() }
        if (owner == null || regeln == null || katalog == null) {
            meldung("Archiv oder Freigaben konnten nicht geladen werden.")
            return
        }
        val dokumente = katalog.map { it.dokument }
        val umfang = FamilienBerechtigungen.Bereich.entries
        val empfaenger = listOf(peer) + weitereEmpfaenger
        fun speichern(anpassen: (String, FamilienArchivFreigaben.Regeln) -> FamilienArchivFreigaben.Regeln) {
            if (!entsperrt || !appImVordergrund) return
            familienAbbrechen()
            val aktuell = runCatching { familienArchivKatalog(owner).map { it.dokument } }.getOrNull()
            val anzahl = if (aktuell == null) 0 else empfaenger.count { ziel ->
                val alt = store.lesen(owner, ziel) ?: return@count false
                store.speichern(owner, ziel, anpassen(ziel, alt), aktuell)
            }
            FamilienHintergrundDienst.sitzungenBeenden()
            if (familienSeite) familienStatusAnzeige?.let(::familienEmpfangStarten)
            if (anzahl != empfaenger.size) meldung("Freigaben gespeichert für $anzahl von ${empfaenger.size} Geräten.")
            else if (direkt.isEmpty()) familienArchivRegelnAnzeigen(peer)
            else {
                if (!startseiteAktiv) listeAktualisieren()
                meldung("Familienfreigabe gespeichert.")
            }
        }
        val mappen = runCatching { UnterlagenMappen(this).laden() }.getOrNull() ?: return
        fun umfangName(bereich: FamilienBerechtigungen.Bereich, id: String): String = when (bereich) {
            FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV -> "Ganzes Archiv"
            FamilienBerechtigungen.Bereich.KATEGORIE -> "Kategorie: $id"
            FamilienBerechtigungen.Bereich.MAPPE -> "Mappe: " + (mappen.find { it.id == id }?.name ?: id)
            FamilienBerechtigungen.Bereich.DOKUMENT -> katalog.find { it.dokument.id == id }?.titel ?: id
        }
        fun rechte(bereich: FamilienBerechtigungen.Bereich, id: String, weitereIds: List<String> = emptyList()) {
            val ids = listOf(id) + weitereIds
            val bisher = if (weitereIds.isEmpty() && weitereEmpfaenger.isEmpty()) regeln.freigaben.find { it.bereich == bereich && it.bereichId == id } else null
            val einzeln = bereich == FamilienBerechtigungen.Bereich.DOKUMENT
            val importMoeglich = bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV ||
                bereich == FamilienBerechtigungen.Bereich.KATEGORIE
            val optionen = mutableListOf("Lesen · Dokumente ansehen", "Lesen · In Suche anzeigen", "Lesen · Assistent verwenden",
                "Bearbeiten · Umbenennen", "Organisieren · Verschieben", "Erweitert · Löschung anfragen")
            val aktionen = listOf(FamilienBerechtigungen.Aktion.ANSEHEN, FamilienBerechtigungen.Aktion.SUCHEN,
                FamilienBerechtigungen.Aktion.ASSISTENT, FamilienBerechtigungen.Aktion.UMBENENNEN,
                FamilienBerechtigungen.Aktion.VERSCHIEBEN, FamilienBerechtigungen.Aktion.LOESCHEN)
            val autoIndex = optionen.size
            if (!einzeln) optionen.add("Neue passende Dokumente automatisch freigeben")
            val importIndex = optionen.size
            if (importMoeglich) optionen.add("Hinzufügen · PDFs einreichen")
            val werte = BooleanArray(optionen.size)
            aktionen.forEachIndexed { index, aktion ->
                werte[index] = bisher?.erlaubteAktionen?.contains(aktion) ?: (index < 2)
            }
            if (!einzeln) werte[autoIndex] = bisher?.neueDokumenteAutomatisch ?: false
            if (importMoeglich) werte[importIndex] = bisher?.erlaubteAktionen?.contains(FamilienBerechtigungen.Aktion.IMPORTIEREN) ?: false
            AlertDialog.Builder(this).setTitle(if (ids.size == 1) umfangName(bereich, id) else "${ids.size} Dokumente freigeben")
                .setMultiChoiceItems(optionen.toTypedArray(), werte.copyOf()) { _, index, gewaehlt -> werte[index] = gewaehlt }
                .setPositiveButton("Freigeben") { _, _ ->
                    if (!werte[0] && !(importMoeglich && werte[importIndex])) {
                        meldung("Wähle Ansehen oder Einreichen.")
                        return@setPositiveButton
                    }
                    val erlaubt = buildSet {
                        aktionen.forEachIndexed { index, aktion -> if (werte[index]) add(aktion) }
                        if (importMoeglich && werte[importIndex]) add(FamilienBerechtigungen.Aktion.IMPORTIEREN)
                    }
                    val snapshot = if (einzeln) emptySet() else dokumente.filter {
                        when (bereich) {
                            FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV -> true
                            FamilienBerechtigungen.Bereich.KATEGORIE -> it.kategorie == id
                            FamilienBerechtigungen.Bereich.MAPPE -> id in it.mappenIds
                            FamilienBerechtigungen.Bereich.DOKUMENT -> false
                        }
                    }.map { it.id }.toSet()
                    speichern { ziel, alt ->
                        val neu = ids.map { bereichId ->
                            FamilienBerechtigungen.Freigabe(ziel, bereich, bereichId, erlaubt,
                                snapshot, !einzeln && werte[autoIndex], archivEigentuemerId = owner)
                        }
                        alt.copy(freigaben = alt.freigaben.filterNot {
                            it.bereich == bereich && it.bereichId in ids
                        } + neu)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
        }
        if (direkt.isNotEmpty()) {
            val (bereich, id) = direkt.first()
            rechte(bereich, id, direkt.drop(1).map { it.second })
            return
        }
        val optionen = listOf("Freigabe erstellen oder bearbeiten", "Private Dokumente", "Freigabe widerrufen")
        AlertDialog.Builder(this).setTitle("Archivfreigaben")
            .setItems(optionen.toTypedArray()) { _, index ->
                if (!entsperrt || !appImVordergrund) return@setItems
                when (index) {
                    0 -> AlertDialog.Builder(this).setTitle("Freigabeumfang")
                        .setItems(arrayOf("Ganzes Archiv", "Kategorie", "Mappe", "Dokument")) { _, i ->
                            val bereich = umfang[i]
                            val ids = when (bereich) {
                                FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV -> listOf("")
                                FamilienBerechtigungen.Bereich.KATEGORIE -> (kategorien + dokumente.map { it.kategorie }).distinct()
                                FamilienBerechtigungen.Bereich.MAPPE -> mappen.map { it.id }
                                FamilienBerechtigungen.Bereich.DOKUMENT -> dokumente.map { it.id }
                            }
                            if (ids.isEmpty()) meldung("Keine Einträge verfügbar.")
                            else if (bereich == FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV) rechte(bereich, "")
                            else AlertDialog.Builder(this).setTitle("Eintrag auswählen")
                                .setItems(ids.map { id ->
                                    umfangName(bereich, id) +
                                        if (regeln.freigaben.any { it.bereich == bereich && it.bereichId == id }) " · Freigabe vorhanden" else ""
                                }.toTypedArray()) { _, wahl -> rechte(bereich, ids[wahl]) }
                                .setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                        }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                    1 -> {
                        val privat = regeln.privat.toMutableSet()
                        AlertDialog.Builder(this).setTitle("Private Dokumente für dieses Gerät")
                            .setMultiChoiceItems(katalog.map { it.titel }.toTypedArray(),
                                BooleanArray(katalog.size) { katalog[it].dokument.id in privat }) { _, i, gewaehlt ->
                                val id = katalog[i].dokument.id
                                if (gewaehlt) privat.add(id) else privat.remove(id)
                            }.setPositiveButton("Speichern") { _, _ -> speichern { _, alt -> alt.copy(privat = privat) } }
                            .setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                    }
                    2 -> if (regeln.freigaben.isEmpty()) meldung("Keine aktiven Archivfreigaben.")
                    else AlertDialog.Builder(this).setTitle("Freigabe widerrufen")
                        .setItems(regeln.freigaben.map { umfangName(it.bereich, it.bereichId) }.toTypedArray()) { _, i ->
                            speichern { _, alt -> alt.copy(freigaben = alt.freigaben - regeln.freigaben[i]) }
                        }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                }
            }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
    }

    private fun familienFreigabeUmfang(): Set<Pair<FamilienBerechtigungen.Bereich, String>> {
        if (!familienArchivTestVerfuegbar) return emptySet()
        val peers = FamilienGeraeteVertrauen(this).bestaetigtePeers()
        if (peers.isEmpty()) return emptySet()
        val owner = runCatching { FamilienGeraeteIdentitaet.laden().fingerprint }.getOrNull() ?: return emptySet()
        val store = FamilienArchivFreigaben(this)
        return peers.flatMap { store.lesen(owner, it)?.freigaben.orEmpty() }
            .map { it.bereich to it.bereichId }.toSet()
    }

    private fun familienDirektFreigeben(bereich: FamilienBerechtigungen.Bereich, ids: List<String>) {
        if (ids.isEmpty()) return
        val store = FamilienGeraeteVertrauen(this)
        val peers = store.bestaetigtePeers().sorted()
        val umfang = ids.map { bereich to it }
        when (peers.size) {
            0 -> meldung("Kein bestätigtes Familiengerät.")
            1 -> familienArchivRegelnAnzeigen(peers.single(), umfang)
            else -> {
                val auswahl = BooleanArray(peers.size)
                val namen = familienNamen().values.toList()
                AlertDialog.Builder(this).setTitle("Empfänger für diese Freigabe")
                    .setMultiChoiceItems(namen.toTypedArray(), auswahl) { _, i, checked -> auswahl[i] = checked }
                    .setPositiveButton("Berechtigungen wählen") { _, _ ->
                        val gewaehlt = peers.filterIndexed { i, _ -> auswahl[i] }
                        if (gewaehlt.isEmpty()) meldung("Wähle mindestens ein Familiengerät.")
                        else familienArchivRegelnAnzeigen(gewaehlt.first(), umfang, gewaehlt.drop(1))
                    }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
            }
        }
    }

    private fun familienArchivFreigabeStarten(kategorie: String) {
        val ganz = kategorie == "Alle Kategorien"
        val dokumente = dokumente.filter { (id, _) ->
            kategorieVon(id) != mappenImportKategorie && (ganz || kategorieVon(id) == kategorie)
        }
        val mappen = UnterlagenMappen(this).laden()
        AlertDialog.Builder(this).setTitle("Mit Familie freigeben")
            .setItems(arrayOf(if (ganz) "Gesamtes Archiv" else "Diese Kategorie", "Dokumente auswählen", "Mappe auswählen")) { _, index ->
                when (index) {
                    0 -> if (ganz) familienDirektFreigeben(FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV, listOf(""))
                         else familienDirektFreigeben(FamilienBerechtigungen.Bereich.KATEGORIE, listOf(kategorie))
                    1 -> {
                        if (dokumente.isEmpty()) { meldung("Keine Dokumente verfügbar."); return@setItems }
                        val gewaehlt = BooleanArray(dokumente.size)
                        AlertDialog.Builder(this).setTitle("Dokumente auswählen")
                            .setMultiChoiceItems(dokumente.map { it.second }.toTypedArray(), gewaehlt) { _, i, checked -> gewaehlt[i] = checked }
                            .setPositiveButton("Weiter") { _, _ ->
                                val ids = dokumente.filterIndexed { i, _ -> gewaehlt[i] }.map { it.first }
                                if (ids.isEmpty()) meldung("Wähle mindestens ein Dokument.")
                                else familienDirektFreigeben(FamilienBerechtigungen.Bereich.DOKUMENT, ids)
                            }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                    }
                    2 -> if (mappen.isEmpty()) meldung("Keine Mappen verfügbar.")
                         else AlertDialog.Builder(this).setTitle("Mappe auswählen")
                            .setItems(mappen.map { it.name }.toTypedArray()) { _, i ->
                                familienDirektFreigeben(FamilienBerechtigungen.Bereich.MAPPE, listOf(mappen[i].id))
                            }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
                }
            }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
    }

    private fun familienPeerAuswaehlen(aktion: (String) -> Unit) {
        if (!entsperrt || !appImVordergrund) return
        val store = FamilienGeraeteVertrauen(this)
        val peers = store.bestaetigtePeers().sorted()
        if (peers.isEmpty()) { meldung("Kein bestätigtes Familiengerät."); return }
        if (peers.size == 1) { aktion(peers.first()); return }
        AlertDialog.Builder(this).setTitle("Familiengerät für Freigaben auswählen")
            .setItems(peers.map(::familienName).toTypedArray()) { _, index ->
                if (entsperrt && appImVordergrund && store.status(peers[index]) == FamilienGeraeteVertrauen.Status.BESTAETIGT) aktion(peers[index])
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienGeraetBenennen(peer: String) {
        if (!entsperrt || !appImVordergrund) return
        val store = FamilienGeraeteVertrauen(this)
        val eingabe = EditText(this).apply {
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            setText(store.geraeteName(peer).orEmpty())
        }
        AlertDialog.Builder(this).setTitle("Familiengerät benennen").setView(eingabe)
            .setPositiveButton("Speichern") { _, _ ->
                if (entsperrt && appImVordergrund) meldung(if (store.geraeteNameSpeichern(peer, eingabe.text.toString())) "Name gespeichert." else "Name konnte nicht gespeichert werden.")
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienMehrgeraeteAnzeigen() {
        if (!entsperrt || !appImVordergrund) return
        val store = FamilienGeraeteVertrauen(this)
        val peers = store.bestaetigtePeers().sorted()
        if (peers.isEmpty()) { meldung("Keine bestätigten Familiengeräte."); return }
        AlertDialog.Builder(this).setTitle("Bestätigte Familiengeräte")
            .setItems(peers.map(::familienName).toTypedArray()) { _, index ->
                if (!entsperrt || !appImVordergrund) return@setItems
                val peer = peers[index]
                val name = familienName(peer)
                val zuletzt = familienLetzteVerbindung.getLong(peer, 0L)
                val datum = if (zuletzt == 0L) "Noch keine Archivverbindung"
                    else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(zuletzt))
                val owner = runCatching { FamilienGeraeteIdentitaet.laden().fingerprint }.getOrNull()
                val gesendet = owner?.let { FamilienArchivFreigaben(this).lesen(it, peer)?.freigaben?.size } ?: 0
                AlertDialog.Builder(this).setTitle(name)
                    .setItems(arrayOf("Name ändern", "Geräteinformationen und Sicherheit")) { _, i ->
                        if (i == 0) familienGeraetBenennen(peer)
                        else familienGeraetSicherheit(peer, name, datum, gesendet)
                    }.setNegativeButton("‹ Zurück") { _, _ -> familienMehrgeraeteAnzeigen() }.familienSicherAnzeigen()
            }.setNegativeButton("‹ Zurück", null).familienSicherAnzeigen()
    }

    private fun familienGeraetSicherheit(peer: String, name: String, datum: String, gesendet: Int) {
        val store = FamilienGeraeteVertrauen(this)
        AlertDialog.Builder(this).setTitle("Geräteinformationen und Sicherheit")
            .setMessage("$name\nGerät gekoppelt\nLetzte Archivverbindung: $datum\nErteilte Freigaben: $gesendet\n\nFingerabdruck\n" +
                peer.chunked(8).joinToString(" "))
            .setNeutralButton("Gerät sperren") { _, _ ->
                        AlertDialog.Builder(this).setTitle("Gerät dauerhaft sperren?")
                            .setMessage("$name verliert den Zugriff auf dein Archiv.")
                            .setPositiveButton("Dauerhaft sperren") { _, _ ->
                                if (entsperrt && appImVordergrund) {
                                    familienAbbrechen()
                                    val ok = store.geraetWiderrufen(peer)
                                    FamilienHintergrundDienst.sitzungenBeenden()
                                    if (ok) familienLetzteVerbindung.edit().remove(peer).apply()
                                    if (familienSeite) familienTestAnzeigen()
                                    meldung(if (ok) "Gerät gesperrt." else "Sperrung fehlgeschlagen.")
                                }
                            }.setNegativeButton(android.R.string.cancel, null).familienSicherAnzeigen()
            }.setNegativeButton("‹ Zurück") { _, _ -> familienMehrgeraeteAnzeigen() }.familienSicherAnzeigen()
    }

    private fun familienWirksameFreigabenAnzeigen(ausgewaehlt: String? = null) {
        if (!entsperrt || !appImVordergrund) return
        if (ausgewaehlt == null) { familienPeerAuswaehlen { familienWirksameFreigabenAnzeigen(it) }; return }
        val peer = ausgewaehlt.takeIf { FamilienGeraeteVertrauen(this).status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT }
        if (peer == null) { meldung("Kein bestätigtes Familiengerät. Kein Dokumentenzugriff."); return }
        val store = FamilienGeraeteFreigaben(this)
        val sichtbar = store.sichtbareTestdokumente(peer).map { it.id }.toSet()
        val ausnahmen = store.ausnahmen(peer)
        val zeilen = listOf("demo-1", "demo-2").mapIndexed { i, id ->
            "Beispieldokument ${i + 1}: " + when {
                id in ausnahmen -> "Privat – ausdrücklich gesperrt"
                id in sichtbar -> "Ansehen und Suchen erlaubt"
                else -> "Kein Zugriff"
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Wirksame Testfreigaben: ${sichtbar.size} von 2")
            .setMessage(zeilen.joinToString("\n\n") + "\n\nLokale Vorschau. Keine Übertragung privater Dokumente.")
            .setPositiveButton("Schließen", null).show()
    }

    private fun familienAusnahmenAnzeigen(ausgewaehlt: String? = null) {
        if (!entsperrt || !appImVordergrund) return
        if (ausgewaehlt == null) { familienPeerAuswaehlen { familienAusnahmenAnzeigen(it) }; return }
        val peer = ausgewaehlt.takeIf { FamilienGeraeteVertrauen(this).status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT }
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

    private fun familienUmfangAnzeigen(ausgewaehlt: String? = null) {
        if (!entsperrt || !appImVordergrund) return
        if (ausgewaehlt == null) { familienPeerAuswaehlen { familienUmfangAnzeigen(it) }; return }
        val peer = ausgewaehlt.takeIf { FamilienGeraeteVertrauen(this).status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT }
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

    private fun familienGeraeteFreigabenAnzeigen(ausgewaehlt: String? = null) {
        if (!entsperrt || !appImVordergrund) return
        if (ausgewaehlt == null) { familienPeerAuswaehlen { familienGeraeteFreigabenAnzeigen(it) }; return }
        val peer = ausgewaehlt.takeIf { FamilienGeraeteVertrauen(this).status(it) == FamilienGeraeteVertrauen.Status.BESTAETIGT }
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
            .setTitle("Alle Familiengeräte dauerhaft sperren?")
            .setMessage("Die WLAN-Testverbindung wird beendet. Weitere Geräteverbindungen werden in dieser Installation gesperrt. Lokale Testprofile und das Archiv bleiben unverändert. Eine erneute Freigabe ist derzeit nicht vorgesehen.")
            .setPositiveButton("Dauerhaft sperren") { _, _ ->
                if (entsperrt && appImVordergrund) {
                    familienAbbrechen()
                    val ok = runCatching { FamilienGeraeteVertrauen(this).widerrufen() }.getOrDefault(false)
                    FamilienHintergrundDienst.beenden(this)
                    meldung(if (ok) "Geräteverbindungen dauerhaft gesperrt." else "Sperre konnte nicht gespeichert werden.")
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun familienEinladen(weitereAufnahme: Boolean = false, mitArchiv: Boolean = false) {
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
            val vorschauen = mutableListOf<android.graphics.Bitmap>()
            var lokalesDokument: String? = null
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
                }, pdfEmpfangen = { _, bytes -> familienPdfRendern(bytes, vorschauen) }, weitereAufnahme = weitereAufnahme,
                    archiv = if (mitArchiv && familienArchivTestVerfuegbar) familienArchivAustausch(pilot) { lokalesDokument = it } else null
                ) { lokal, partner -> familienBestaetigen(pilot, lokal, partner) }
            }.getOrDefault(false)
            runOnUiThread {
                if (familienPilot === pilot) {
                    familienAbbrechen()
                    if (mitArchiv && erfolg && entsperrt && appImVordergrund) familienTestAnzeigen()
                    if (entsperrt && appImVordergrund && erfolg && lokalesDokument != null) {
                        vorschauen.forEach { it.recycle() }
                        dokumentOeffnen(checkNotNull(lokalesDokument))
                        return@runOnUiThread
                    }
                    if (entsperrt && appImVordergrund && erfolg && vorschauen.isNotEmpty()) {
                        familienPdfAnzeigen(vorschauen)
                        return@runOnUiThread
                    }
                    if (entsperrt && appImVordergrund) meldung(if (erfolg) "Beide Geräte haben zugestimmt. Testdokument gesendet und Empfang vom Partner bestätigt. Die Geräteidentität ist lokal gespeichert; die Sitzung ist beendet." else "Testaustausch nicht abgeschlossen. Prüfe den Kopplungsstatus in Meine Familie.")
                }
                vorschauen.forEach { it.recycle() }
                vorschauen.clear()
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
        if (familienSeite && familienPilot == null) {
            familienEmpfangBeenden()
            familienSeite = false
        }
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

        if (familienArchivTestVerfuegbar) layout.addView(Button(this).apply {
            val scope = if (kategorie == "Alle Kategorien") FamilienBerechtigungen.Bereich.GESAMTES_ARCHIV to ""
                else FamilienBerechtigungen.Bereich.KATEGORIE to kategorie
            text = if (scope in familienFreigabeUmfang()) "Familienfreigaben bearbeiten" else "Mit Familie freigeben"
            setOnClickListener { familienArchivFreigabeStarten(kategorie) }
        })

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

        if (namenDatei.exists()) {
            namenDatei.useLines(Charsets.UTF_8) { zeilen ->
                for (zeile in zeilen) {
                    val teile = zeile.split("|", limit = 2)
                    if (teile.size == 2) dokumente.add(teile[0] to teile[1])
                }
            }
        }
        val ordner = File(filesDir, "dokumente")
        val indexIds = dokumente.map { it.first }.toSet()
        for (datei in ordner.listFiles().orEmpty()) {
            if (datei.name.endsWith(".einreichung")) {
                val id = datei.name.removeSuffix(".einreichung")
                if (!FamilienArchivAustausch.gueltig(id)) continue
                if (id in indexIds) {
                    val original = File(ordner, id)
                    if (!original.exists()) datei.renameTo(original)
                } else {
                    einstellungen.edit().remove(id).apply()
                    datei.delete()
                }
                continue
            }
            if (!datei.name.endsWith(".loeschung")) continue
            val id = datei.name.removeSuffix(".loeschung")
            if (!FamilienArchivAustausch.gueltig(id)) continue
            if (id in indexIds) {
                val original = File(ordner, id)
                if (!original.exists()) datei.renameTo(original)
            } else {
                ocrDatei(id).delete()
                ocrTexte.remove(id)
                einstellungen.edit().remove(id).apply()
                datei.delete()
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

    private fun importOcrStarten(dokumentId: String, pdfDaten: ByteArray, anfrageMappeId: String?, pufferFreigeben: Boolean = false) {
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
            } finally {
                if (pufferFreigeben) pdfDaten.fill(0)
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

                try {
                    dokumentNamenSpeichern(dateiName, pdfDateiname(eingabeName))
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

    private fun pdfDateiname(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"

    private fun dokumentNamenSpeichern(id: String, neuerName: String) {
        check(dokumente.count { it.first == id } == 1)
        dokumentIndexSpeichern(dokumente.map { (dateiname, titel) ->
            dateiname to if (dateiname == id) neuerName else titel
        })
        dokumenteLaden()
    }

    private fun dokumentIndexSpeichern(eintraege: List<Pair<String, String>>) {
        val index = File(filesDir, "dokumentnamen.txt")
        val temp = File(filesDir, "dokumentnamen_neu.tmp")
        try {
            java.io.FileOutputStream(temp).use { ausgabe ->
                for ((id, titel) in eintraege) {
                    ausgabe.write("$id|$titel\n".toByteArray(Charsets.UTF_8))
                }
                ausgabe.fd.sync()
            }
            check(temp.renameTo(index)) { "Namensliste konnte nicht gespeichert werden" }
        } finally {
            temp.delete()
        }
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
                    setTextColor(sekundaerTextfarbe)
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

        val familienUmfang = familienFreigabeUmfang()
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
                    kartenFarbe,
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
                    setTextColor(sekundaerTextfarbe)
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

            if (familienArchivTestVerfuegbar) karte.addView(Button(this).apply {
                val freigabe = FamilienBerechtigungen.Bereich.DOKUMENT to dokument.first
                text = if (freigabe in familienUmfang) "Familienfreigabe bearbeiten" else "Mit Familie freigeben"
                setOnClickListener { familienDirektFreigeben(FamilienBerechtigungen.Bereich.DOKUMENT, listOf(dokument.first)) }
            })

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

        val geloescht = runCatching { dokumentEntfernen(dateiName) }.getOrDefault(false)
        meldung(getString(if (geloescht) R.string.ui_text_046 else R.string.ui_text_047))
    }

    private fun dokumentEntfernen(id: String): Boolean {
        if (!FamilienArchivAustausch.gueltig(id) || ocrLaeuft || id in laufendeImportOcr) return false
        val datei = File(filesDir, "dokumente/$id")
        val zwischenstand = File(datei.parentFile, "$id.loeschung")
        if (!datei.isFile || zwischenstand.exists() || !datei.renameTo(zwischenstand)) return false
        val uebrige = dokumente.filterNot { it.first == id }
        try {
            dokumentIndexSpeichern(uebrige)
        } catch (e: Exception) {
            zwischenstand.renameTo(datei)
            throw e
        }
        // With the index committed, startup can finish cleanup if the process stops here.
        ocrDatei(id).delete()
        ocrTexte.remove(id)
        einstellungen.edit().remove(id).apply()
        dokumenteLaden()
        listeAktualisieren()
        ocrStatusAktualisieren()
        return !zwischenstand.exists()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!entsperrt) {
            finish()
            return
        }
        if (familienGeteiltSeite) {
            familienPilot?.close()
            familienPilot = null
            familienArchivNavigation = null
            familienTestAnzeigen()
        } else if (familienSeite) {
            familienAbbrechen()
            familienSeite = false
            startseiteAnzeigen()
        } else if (!startseiteAktiv) {
            startseiteAnzeigen()
        } else {
            super.onBackPressed()
        }
    }
}
