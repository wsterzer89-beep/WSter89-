package de.dokumentenpilot.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {

    private var anzahl = 0

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

        val zaehler = TextView(this).apply {
            text = "Testdokumente: 0"
            textSize = 20f
        }

        val knopf = Button(this).apply {
            text = "Testdokument hinzufügen"
            setOnClickListener {
                anzahl++
                zaehler.text = "Testdokumente: $anzahl"
            }
        }

        layout.addView(titel)
        layout.addView(zaehler)
        layout.addView(knopf)

        setContentView(layout)
    }
}
