package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FinanzierungsListeTest {
    private val liste = """
        • Darlehensvermittlungsauftrag
        • Bestätigung zum Datenschutz und Unterlagenerhalt
        • Vollständig ausgefüllte und unterzeichnete Selbstauskunft
        • Steuer – Identifikationsnummer
        • Kopie Personalausweis
        • Lohn-/ und Gehaltsnachweise (mind. 3 Monate)
        • Lohn-/ und Gehaltsnachweise (Dez. letzten Jahres, alternativ
        Lohnsteuerbescheinigung)
        • ggf. Einkommensteuererklärung und Einkommensteuerbescheide (3 Jahre)
        • ggf. Jahresabschlüsse (3 Jahre) und aktuelle BWA mit Summen- und
        Saldenlisten
        • aktuelle Renteninformation
        • Eigenkapitalnachweis
        • Vermögensübersicht (Sparverträge, Bausparen, Versicherungen, etc.)
        • ggf. bestehende Kreditverträge mit aktueller Saldenbestätigung
        • ggf. Nachweise über Unterhaltsverpflichtungen oder Unterhaltsberechtigung
        • ggf. private Krankenversicherung
        • aktueller Grundbuchauszug
        • Lageplan / Auszug aus dem Liegenschaftskataster / Flurkarte
        • Entwurf des notariellen Kaufvertrages
        • Gesamtkostenaufstellung (Musterberechnung mit Angaben zum Eigenkapital
        und Eigenleistungen)
        • Wohnflächenberechnung
        • Ansichten / Grundrisse / Schnitte des Gebäudes
        • Notarieller Kaufvertrag (folgt später)
        • Verbraucherbauvertrag
        • Feuerrohbauversicherung (folgt später)
        • Baugenehmigung (folgt später)
        • Exposee bei Bestandsobjekten
        • Objektfotos (Innen und Außen) bei Bestandsobjekten
        • bestehende Gebäudeversicherung des Voreigentümers bei Bestandsobjekten
    """.trimIndent()

    @Test
    fun alle28ListenpunkteBleibenErhalten() {
        val punkte = UnterlagenErkennung.listenpunkteErkennen(liste)
        assertEquals(28, punkte.size)
        assertTrue(punkte[6].original.contains("Lohnsteuerbescheinigung"))
        assertTrue(punkte[8].original.contains("Saldenlisten"))
        assertTrue(punkte[18].original.contains("Eigenleistungen"))
        assertEquals(8, punkte.count { it.bedingt })
        assertTrue(punkte.takeLast(3).all { it.bedingt })
        assertEquals(3, punkte.count { it.spaeter })
    }
}
