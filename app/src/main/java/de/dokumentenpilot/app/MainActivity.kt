
package de.dokumentenpilot.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val PDF_AUSWAHL = 100
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

        val beschreibung = TextView(this).apply {
            text = "Deine Dokumente an einem Ort"
            textSize = 18f
        }

        val pdfKnopf = Button(this).apply {
            text = "PDF hinzufügen"
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
        layout.addView(beschreibung)
        layout.addView(pdfKnopf)
        layout.addView(anzahlAnzeige)
        layout.addView(scrollBereich)

        setContentView(layout)
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
            var dateiname = "Unbekanntes Dokument"

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

            dokumente.add(dateiname)
            dokumentenListeAktualisieren()
        }
    }

    private fun dokumentenListeAktualisieren() {
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
}
