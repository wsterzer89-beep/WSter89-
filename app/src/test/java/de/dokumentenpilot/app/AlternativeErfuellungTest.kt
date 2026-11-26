package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class AlternativeErfuellungTest {
    @Test
    fun alleKombinationen() {
        val punkt = UnterlagenErkennung.listenpunkteErkennen(
            "• Geburtsurkunde oder Personalausweis\n• Energieausweis"
        ).first()
        for (geburt in 0..1) for (ausweis in 0..1) {
            assertEquals(geburt + ausweis > 0,
                UnterlagenErkennung.alternativeErfuellt(punkt) {
                    if (it.dokumentenart == "Geburtsurkunde") geburt else ausweis
                })
        }
    }
}
