
package de.dokumentenpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
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
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {

    private val PDF_AUSWAHL = 100
    private val schluesselName = "DokumentenPilotSchluessel"

    private val dokumente =
        mutableListOf<Pair<String, String>>()

    private val kategorien = listOf(
        "Allgemein",
        "Arbeit",
        "Bank & Kredite",
        "Wohnen",
        "Versicherungen",
        "Familie & Behörden"
    )

    private val dunkelblau = Color.rgb(25, 55, 110)
    private val hellblau = Color.rgb(240, 245, 255)

    private lateinit var dokumentenListe: LinearLayout
    private lateinit var anzahlAnzeige: TextView
    private lateinit var suche: EditText

    private var importKategorie = "Allgemein"
    private var aktiveKategorie = "Alle Kategorien"
    private var startseiteAktiv = true

    private val einstellungen by lazy {
        getSharedPreferences("dokument_kategorien", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(hellblau)
        }
    }

    private fun titelText(textwert: String): TextView {
        return TextView(this).apply {
            text = textwert
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

    private fun anzahlFuer(kategorie: String): Int {
        return dokumente.count {
            kategorieVon(it.first) == kategorie
        }
    }

    private fun startseiteAnzeigen() {
        startseiteAktiv = true

        val layout = grundLayout()

        layout.addView(titelText("DokumentenPilot"))

        layout.addView(TextView(this).apply {
            text = "Deine Dokumente sicher verwalten"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(dp(4), 0, 0, dp(18))
        })

        layout.addView(
            dunkelblauerButton(
                "＋ PDF SICHER HINZUFÜGEN"
            ) {
                kategorieFuerImportWaehlen()
            }
        )

        layout.addView(TextView(this).apply {
            text = "Meine Kategorien"
            textSize = 21f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(20), 0, dp(14))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val inhalt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val kacheln = listOf(
            "💼" to "Arbeit",
            "🏦" to "Bank & Kredite",
            "🏠" to "Wohnen",
            "🛡️" to "Versicherungen",
            "📁" to "Allgemein",
            "🏛️" to "Familie & Behörden"
        )

        kacheln.chunked(2).forEach { paar ->
            val zeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            paar.forEach { eintrag ->
                val kategorie = eintrag.second

                val karte = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8), dp(16), dp(8), dp(16)
                    )
                    background = hintergrund(Color.WHITE, 16f)
                    elevation = dp(3).toFloat()
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        dokumentenAnsichtAnzeigen(kategorie)
                    }
                }

                karte.addView(TextView(this).apply {
                    text = eintrag.first
                    textSize = 32f
                    gravity = Gravity.CENTER
                })

                karte.addView(TextView(this).apply {
                    text = kategorie
                    textSize = 15f
                    setTextColor(dunkelblau)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(5))
                })

                karte.addView(TextView(this).apply {
                    val anzahl = anzahlFuer(kategorie)
                    text = if (anzahl == 1) {
                        "1 Dokument"
                    } else {
                        "$anzahl Dokumente"
                    }
                    textSize = 12f
                    setTextColor(Color.DKGRAY)
                    gravity = Gravity.CENTER
                })

                zeile.addView(
                    karte,
                    LinearLayout.LayoutParams(
                        0, dp(135), 1f
                    ).apply {
                        setMargins(
                            dp(4), dp(4), dp(8), dp(10)
                        )
                    }
                )
            }

            inhalt.addView(zeile)
        }

        val scanKarte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = hintergrund(Color.WHITE, 16f)
            elevation = dp(3).toFloat()

            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Dokumentenscanner kommt bald",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        scanKarte.addView(TextView(this).apply {
            text = "📷 Dokument scannen"
            textSize = 18f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        scanKarte.addView(TextView(this).apply {
            text = "In Vorbereitung"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })

        inhalt.addView(
            scanKarte,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(95)
            ).apply {
                setMargins(dp(4), dp(8), dp(8), dp(12))
            }
        )

        inhalt.addView(
            dunkelblauerButton("ALLE DOKUMENTE ANZEIGEN") {
                dokumentenAnsichtAnzeigen("Alle Kategorien")
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

    private fun dokumentenAnsichtAnzeigen(
        kategorie: String
    ) {
        startseiteAktiv = false
        aktiveKategorie = kategorie

        val layout = grundLayout()

        layout.addView(
            dunkelblauerButton("← ZUR STARTSEITE") {
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
            dunkelblauerButton("＋ PDF HINZUFÜGEN") {
                kategorieFuerImportWaehlen()
            }
        )

        layout.addView(TextView(this).apply {
            text = "Dokumente durchsuchen"
            textSize = 16f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(18), 0, dp(6))
        })

        suche = EditText(this).apply {
            hint = "Nach Dokumentnamen suchen ..."
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = hintergrund(Color.WHITE, 14f)

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
            setPadding(dp(4), dp(18), 0, dp(12))
        }

        layout.addView(anzahlAnzeige)

        dokumentenListe = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(10))
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
            .setItems(kategorien.toTypedArray()) { _, position ->
                importKategorie = kategorien[position]

                val intent = Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                ).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }

                try {
                    startActivityForResult(intent, PDF_AUSWAHL)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        "Keine Dateiauswahl verfügbar",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun kategorieVon(dateiName: String): String {
        return einstellungen.getString(
            dateiName, "Allgemein"
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

    private fun kategorieAendern(dateiName: String) {
        val aktuell = kategorieVon(dateiName)
        val position = kategorien.indexOf(aktuell)
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

    private fun schluesselHolen(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val vorhanden = keyStore.getKey(
            schluesselName, null
        ) as? SecretKey

        if (vorhanden != null) return vorhanden

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val parameter = KeyGenParameterSpec.Builder(
            schluesselName,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
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

        val ordner = File(filesDir, "dokumente")
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
        ).appendText("${datei.name}|$dateiname\n")

        kategorieSpeichern(datei.name, kategorie)
    }

    private fun dokumenteLaden() {
        dokumente.clear()

        val namenDatei = File(
            filesDir, "dokumentnamen.txt"
        )

        if (!namenDatei.exists()) return

        namenDatei.forEachLine { zeile ->
            val teile = zeile.split("|", limit = 2)

            if (teile.size == 2) {
                dokumente.add(teile[0] to teile[1])
            }
        }
    }

    private fun listeAktualisieren() {
        if (startseiteAktiv ||
            !::dokumentenListe.isInitialized ||
            !::anzahlAnzeige.isInitialized ||
            !::suche.isInitialized
        ) return

        dokumentenListe.removeAllViews()

        val suchtext = suche.text.toString().trim()

        val sichtbareDokumente = dokumente.filter {
            val namePasst = it.second.contains(
                suchtext, ignoreCase = true
            )

            val kategoriePasst =
                aktiveKategorie == "Alle Kategorien" ||
                    kategorieVon(it.first) == aktiveKategorie

            namePasst && kategoriePasst
        }

        anzahlAnzeige.text =
            "Dokumente: ${sichtbareDokumente.size}"

        if (sichtbareDokumente.isEmpty()) {
            dokumentenListe.addView(TextView(this).apply {
                text = "Keine Dokumente gefunden"
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(dp(12), dp(25), 0, dp(20))
            })
            return
        }

        sichtbareDokumente.forEach { dokument ->
            val karte = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(12), dp(12), dp(12), dp(12)
                )
                background = hintergrund(Color.WHITE, 16f)
                elevation = dp(3).toFloat()
            }

            karte.addView(TextView(this).apply {
                text = "📄 ${dokument.second}"
                textSize = 17f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
                setOnClickListener {
                    dokumentOeffnen(dokument.first)
                }
            })

            karte.addView(TextView(this).apply {
                text = "📁 ${kategorieVon(dokument.first)}"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, dp(8))
            })

            val knopfZeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val oeffnenButton =
                dunkelblauerButton("Öffnen") {
                    dokumentOeffnen(dokument.first)
                }

            val ordnerButton = Button(this).apply {
                text = "Ordner"
                textSize = 12f
                setOnClickListener {
                    kategorieAendern(dokument.first)
                }
            }

            val loeschenButton = Button(this).apply {
                text = "Löschen"
                textSize = 12f
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Dokument löschen?")
                        .setMessage(
                            "Möchtest du dieses Dokument wirklich löschen?"
                        )
                        .setPositiveButton("Löschen") { _, _ ->
                            dokumentLoeschen(dokument.first)
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }

            knopfZeile.addView(
                oeffnenButton,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            knopfZeile.addView(
                ordnerButton,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            knopfZeile.addView(
                loeschenButton,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            karte.addView(knopfZeile)

            dokumentenListe.addView(
                karte,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(4), 0, dp(14))
                }
            )
        }
    }

    private fun dokumentOeffnen(dateiName: String) {
        try {
            val verschluesselteDatei = File(
                File(filesDir, "dokumente"),
                dateiName
            )

            val daten = verschluesselteDatei.readBytes()
            val ivLaenge = daten[0].toInt() and 0xFF

            require(ivLaenge == 12)
            require(daten.size > ivLaenge + 1)

            val iv = daten.copyOfRange(
                1, 1 + ivLaenge
            )

            val verschluesselt = daten.copyOfRange(
                1 + ivLaenge, daten.size
            )

            val cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                schluesselHolen(),
                GCMParameterSpec(128, iv)
            )

            val entschluesselt =
                cipher.doFinal(verschluesselt)

            val vorschauOrdner = File(
                cacheDir, "pdf_vorschau"
            )
            vorschauOrdner.mkdirs()

            vorschauOrdner.listFiles()
                ?.forEach { it.delete() }

            val pdfDatei = File(
                vorschauOrdner, "vorschau.pdf"
            )
            pdfDatei.writeBytes(entschluesselt)

            val uri = FileProvider.getUriForFile(
                this,
                "de.dokumentenpilot.app.fileprovider",
                pdfDatei
            )

            val intent = Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(uri, "application/pdf")
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
            requestCode, resultCode, data
        )

        if (requestCode != PDF_AUSWAHL ||
            resultCode != RESULT_OK
        ) return

        val uri = data?.data ?: return
        var dateiname = "Dokument.pdf"

        try {
            contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0 && cursor.moveToFirst()) {
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
                dateiname, daten, importKategorie
            )

            dokumenteLaden()

            if (startseiteAktiv) {
                startseiteAnzeigen()
            } else {
                listeAktualisieren()
            }

            Toast.makeText(
                this,
                "Dokument gespeichert",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speichern fehlgeschlagen",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dokumentLoeschen(dateiName: String) {
        try {
            val datei = File(
                File(filesDir, "dokumente"),
                dateiName
            )

            if (!datei.exists() || !datei.delete()) {
                Toast.makeText(
                    this,
                    "Datei konnte nicht gelöscht werden",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val namenDatei = File(
                filesDir, "dokumentnamen.txt"
            )

            val uebrige = dokumente.filter {
                it.first != dateiName
            }

            namenDatei.writeText(
                uebrige.joinToString("\n") {
                    "${it.first}|${it.second}"
                } + if (uebrige.isNotEmpty()) {
                    "\n"
                } else {
                    ""
                }
            )

            einstellungen.edit()
                .remove(dateiName)
                .apply()

            dokumenteLaden()
            listeAktualisieren()

            Toast.makeText(
                this,
                "Dokument gelöscht",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Löschen fehlgeschlagen",
                Toast.LENGTH_LONG
            ).show()
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
