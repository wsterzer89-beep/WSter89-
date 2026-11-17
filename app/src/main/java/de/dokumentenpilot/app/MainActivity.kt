
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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
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
    private lateinit var kategorieFilter: Spinner
    private lateinit var suche: EditText

    private var importKategorie = "Allgemein"

    private val einstellungen by lazy {
        getSharedPreferences("dokument_kategorien", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 15)
            setBackgroundColor(hellblau)
        }

        val titel = TextView(this).apply {
            text = "DokumentenPilot"
            textSize = 27f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 8, 8, 4)
        }

        val untertitel = TextView(this).apply {
            text = "Deine Dokumente sicher verwalten"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(8, 0, 8, 20)
        }

        val pdfKnopf = Button(this).apply {
            text = "＋ PDF sicher hinzufügen"
            setTextColor(Color.WHITE)
            backgroundTintList =
                ColorStateList.valueOf(dunkelblau)

            setOnClickListener {
                kategorieFuerImportWaehlen()
            }
        }

        val sucheTitel = TextView(this).apply {
            text = "Dokumente durchsuchen"
            textSize = 16f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 20, 8, 5)
        }

        suche = EditText(this).apply {
            hint = "Nach Dokumentnamen suchen ..."
            textSize = 15f
            setSingleLine(true)
            setPadding(20, 12, 20, 12)
            background = hintergrund(
                Color.WHITE, 18f
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

        val filterTitel = TextView(this).apply {
            text = "Kategorie"
            textSize = 16f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 18, 8, 5)
        }

        kategorieFilter = Spinner(this).apply {
            val auswahl = listOf(
                "Alle Kategorien"
            ) + kategorien

            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                auswahl
            )

            onItemSelectedListener =
                object : android.widget.AdapterView
                    .OnItemSelectedListener {

                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        listeAktualisieren()
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?
                    ) {}
                }
        }

        anzahlAnzeige = TextView(this).apply {
            text = "Dokumente: 0"
            textSize = 19f
            setTextColor(dunkelblau)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 20, 8, 12)
        }

        dokumentenListe = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollBereich = ScrollView(this).apply {
            fillViewport = true
            addView(dokumentenListe)
        }

        layout.addView(titel)
        layout.addView(untertitel)
        layout.addView(pdfKnopf)
        layout.addView(sucheTitel)
        layout.addView(suche)
        layout.addView(filterTitel)
        layout.addView(kategorieFilter)
        layout.addView(anzahlAnzeige)

        layout.addView(
            scrollBereich,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        dokumenteLaden()
        listeAktualisieren()
    }

    private fun hintergrund(
        farbe: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(farbe)
            cornerRadius = radius
        }
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
        val keyStore = KeyStore.getInstance(
            "AndroidKeyStore"
        )
        keyStore.load(null)

        val vorhanden = keyStore.getKey(
            schluesselName,
            null
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

        if (!namenDatei.exists()) return

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

    private fun listeAktualisieren() {
        if (!::dokumentenListe.isInitialized ||
            !::anzahlAnzeige.isInitialized ||
            !::suche.isInitialized ||
            !::kategorieFilter.isInitialized
        ) return

        dokumentenListe.removeAllViews()

        val suchtext = suche.text.toString()
            .trim()

        val filter = kategorieFilter
            .selectedItem?.toString()
            ?: "Alle Kategorien"

        val sichtbareDokumente = dokumente.filter {
            val namePasst = it.second.contains(
                suchtext,
                ignoreCase = true
            )

            val kategoriePasst =
                filter == "Alle Kategorien" ||
                    kategorieVon(it.first) == filter

            namePasst && kategoriePasst
        }

        anzahlAnzeige.text =
            "Dokumente: ${dokumente.size}" +
                if (sichtbareDokumente.size != dokumente.size) {
                    " · Angezeigt: ${sichtbareDokumente.size}"
                } else {
                    ""
                }

        if (sichtbareDokumente.isEmpty()) {
            val leer = TextView(this).apply {
                text = "Keine Dokumente gefunden"
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(15, 30, 15, 20)
            }
            dokumentenListe.addView(leer)
            return
        }

        sichtbareDokumente.forEach { dokument ->

            val karte = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = hintergrund(
                    Color.WHITE,
                    22f
                )
                elevation = 5f
            }

            val dateiname = TextView(this).apply {
                text = "📄 ${dokument.second}"
                textSize = 17f
                setTextColor(dunkelblau)
                setTypeface(null, Typeface.BOLD)
                setPadding(4, 4, 4, 12)
                setOnClickListener {
                    dokumentOeffnen(dokument.first)
                }
            }

            val kategorieText = TextView(this).apply {
                text = "📁 ${kategorieVon(dokument.first)}"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(4, 0, 4, 10)
            }

            val knopfZeile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val oeffnenButton = Button(this).apply {
                text = "Öffnen"
                setTextColor(Color.WHITE)
                backgroundTintList =
                    ColorStateList.valueOf(dunkelblau)
                setOnClickListener {
                    dokumentOeffnen(dokument.first)
                }
            }

            val ordnerButton = Button(this).apply {
                text = "Ordner"
                setOnClickListener {
                    kategorieAendern(dokument.first)
                }
            }

            val loeschenButton = Button(this).apply {
                text = "Löschen"
                setOnClickListener {
                    AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle("Dokument löschen?")
                        .setMessage(
                            "Möchtest du dieses Dokument wirklich löschen?"
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
            }

            val knopfParameter =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

            knopfZeile.addView(
                oeffnenButton,
                LinearLayout.LayoutParams(knopfParameter)
            )
            knopfZeile.addView(
                ordnerButton,
                LinearLayout.LayoutParams(knopfParameter)
            )
            knopfZeile.addView(
                loeschenButton,
                LinearLayout.LayoutParams(knopfParameter)
            )

            karte.addView(dateiname)
            karte.addView(kategorieText)
            karte.addView(knopfZeile)

            val abstand =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4, 5, 4, 18)
                }

            dokumentenListe.addView(
                karte,
                abstand
            )
        }
    }

    private fun dokumentOeffnen(
        dateiName: String
    ) {
        try {
            val verschluesselteDatei = File(
                File(filesDir, "dokumente"),
                dateiName
            )

            val daten =
                verschluesselteDatei.readBytes()

            val ivLaenge =
                daten[0].toInt() and 0xFF

            require(ivLaenge == 12)
            require(daten.size > ivLaenge + 1)

            val iv = daten.copyOfRange(
                1,
                1 + ivLaenge
            )

            val verschluesselt =
                daten.copyOfRange(
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

            val entschluesselt =
                cipher.doFinal(verschluesselt)

            val vorschauOrdner = File(
                cacheDir,
                "pdf_vorschau"
            )
            vorschauOrdner.mkdirs()

            vorschauOrdner.listFiles()
                ?.forEach { it.delete() }

            val pdfDatei = File(
                vorschauOrdner,
                "vorschau.pdf"
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

        if (requestCode != PDF_AUSWAHL ||
            resultCode != RESULT_OK
        ) return

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

                if (index >= 0 &&
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
            listeAktualisieren()

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

    private fun dokumentLoeschen(
        dateiName: String
    ) {
        try {
            val datei = File(
                File(filesDir, "dokumente"),
                dateiName
            )

            if (!datei.exists() ||
                !datei.delete()
            ) {
                Toast.makeText(
                    this,
                    "Datei konnte nicht gelöscht werden",
                    Toast.LENGTH_LONG
                ).show()
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
}
