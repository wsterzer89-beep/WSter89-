
package de.dokumentenpilot.app
import android.app.AlertDialog
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
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
    private val dokumente = mutableListOf<Pair<String, String>>()

    private lateinit var dokumentenListe: LinearLayout
    private lateinit var anzahlAnzeige: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val titel = TextView(this).apply {
            text = "DokumentenPilot"
            textSize = 26f
        }

        val pdfKnopf = Button(this).apply {
            text = "PDF sicher hinzufügen"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                startActivityForResult(intent, PDF_AUSWAHL)
            }
        }

        anzahlAnzeige = TextView(this).apply {
            text = "Dokumente: 0"
            textSize = 20f
        }

        dokumentenListe = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollBereich = ScrollView(this).apply {
            addView(dokumentenListe)
        }

        layout.addView(titel)
        layout.addView(pdfKnopf)
        layout.addView(anzahlAnzeige)
        layout.addView(scrollBereich)

        setContentView(layout)

        dokumenteLaden()
        listeAktualisieren()
    }

    private fun schluesselHolen(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val vorhanden =
            keyStore.getKey(schluesselName, null) as? SecretKey

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
        daten: ByteArray
    ) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, schluesselHolen())

        val iv = cipher.iv
        val verschluesselt = cipher.doFinal(daten)

        val ordner = File(filesDir, "dokumente")
        ordner.mkdirs()

        val datei = File(ordner, "${UUID.randomUUID()}.dp")

        datei.outputStream().use { ausgabe ->
            ausgabe.write(iv.size)
            ausgabe.write(iv)
            ausgabe.write(verschluesselt)
        }

        File(filesDir, "dokumentnamen.txt")
            .appendText("${datei.name}|$dateiname\n")
    }

    private fun dokumenteLaden() {
        dokumente.clear()

        val namenDatei = File(filesDir, "dokumentnamen.txt")
        if (!namenDatei.exists()) return

        namenDatei.forEachLine { zeile ->
            val teile = zeile.split("|", limit = 2)

            if (teile.size == 2) {
                dokumente.add(teile[0] to teile[1])
            }
        }
    }

    private fun listeAktualisieren() {
        anzahlAnzeige.text = "Dokumente: ${dokumente.size}"
        dokumentenListe.removeAllViews()

        dokumente.forEachIndexed { index, dokument ->
            val eintrag = Button(this).apply {
                text = "${index + 1}. ${dokument.second}"
                setOnClickListener {
                    dokumentOeffnen(dokument.first)
                }
            }
            val loeschenButton = Button(this@MainActivity).apply {
    text = "Löschen"
    setOnClickListener {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Dokument löschen?")
            .setMessage("Möchtest du dieses Dokument wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                dokumentLoeschen(dokument.first)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
                        
            

        
        dokumentenListe.addView(eintrag)
            dokumentenListe.addView(loeschenButton)
        }
    
    }

    private fun dokumentOeffnen(dateiName: String) {
        try {
            val verschluesselteDatei =
                File(File(filesDir, "dokumente"), dateiName)

            val daten = verschluesselteDatei.readBytes()

            val ivLaenge = daten[0].toInt() and 0xFF
            require(ivLaenge == 12)
            require(daten.size > ivLaenge + 1)

            val iv = daten.copyOfRange(1, 1 + ivLaenge)
            val verschluesselt =
                daten.copyOfRange(1 + ivLaenge, daten.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                schluesselHolen(),
                GCMParameterSpec(128, iv)
            )

            val entschluesselt = cipher.doFinal(verschluesselt)

            val vorschauOrdner = File(cacheDir, "pdf_vorschau")
            vorschauOrdner.mkdirs()

            vorschauOrdner.listFiles()?.forEach { it.delete() }

            val pdfDatei = File(vorschauOrdner, "vorschau.pdf")
            pdfDatei.writeBytes(entschluesselt)

            val uri = FileProvider.getUriForFile(
                this,
                "de.dokumentenpilot.app.fileprovider",
                pdfDatei
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PDF_AUSWAHL &&
            resultCode == RESULT_OK
        ) {
            val uri = data?.data ?: return
            var dateiname = "Dokument.pdf"

            contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0 && cursor.moveToFirst()) {
                    dateiname = cursor.getString(index)
                }
            }

            try {
                val daten = contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: return

                dokumentVerschluesseln(dateiname, daten)
                dokumenteLaden()
                listeAktualisieren()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Speichern fehlgeschlagen",
                    Toast.LENGTH_LONG
                ).show()
            }
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

            val namenDatei = File(filesDir, "dokumentnamen.txt")
            val uebrige = dokumente.filter {
                it.first != dateiName
            }

            namenDatei.writeText(
                uebrige.joinToString("\n") {
                    "${it.first}|${it.second}"
                } + if (uebrige.isNotEmpty()) "\n" else ""
            )

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
