
package de.dokumentenpilot.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {

    private val PDF_AUSWAHL = 100
    private val schluesselName = "DokumentenPilotSchluessel"
    private val dokumente = mutableListOf<String>()

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

        val vorhandenerSchluessel =
            keyStore.getKey(schluesselName, null) as? SecretKey

        if (vorhandenerSchluessel != null) {
            return vorhandenerSchluessel
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

        val datei = File(
            ordner,
            "${System.currentTimeMillis()}.dp"
        )

        datei.outputStream().use { ausgabe ->
            ausgabe.write(iv.size)
            ausgabe.write(iv)
            ausgabe.write(verschluesselt)
        }

        val namenDatei = File(filesDir, "dokumentnamen.txt")
        namenDatei.appendText("${datei.name}|$dateiname\n")
    }

    private fun dokumenteLaden() {
        dokumente.clear()

        val namenDatei = File(filesDir, "dokumentnamen.txt")

        if (namenDatei.exists()) {
            namenDatei.forEachLine { zeile ->
                val teile = zeile.split("|", limit = 2)

                if (teile.size == 2) {
                    dokumente.add(teile[1])
                }
            }
        }
    }

    private fun listeAktualisieren() {
        anzahlAnzeige.text = "Dokumente: ${dokumente.size}"
        dokumentenListe.removeAllViews()

        dokumente.forEachIndexed { index, name ->
            val eintrag = TextView(this).apply {
                text = "${index + 1}. 📄 $name"
                textSize = 18f
                setPadding(0, 20, 0, 20)
            }

            dokumentenListe.addView(eintrag)
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
                uri,
                null,
                null,
                null,
                null
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

                dokumente.add(dateiname)
                listeAktualisieren()

            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    this,
                    "Speichern fehlgeschlagen",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
