package de.dokumentenpilot.app

internal object MappenImportRegeln {
    const val KATEGORIE = "__intern_mappenimport_v1__"

    fun loeschbareIds(
        ids: List<String>,
        referenzen: Set<String>,
        laufendeOcr: Set<String>,
        vorhandeneIds: Set<String>,
        kategorieVon: (String) -> String
    ): List<String> = ids.distinct().filter { id ->
        id in vorhandeneIds && id !in referenzen && id !in laufendeOcr &&
            kategorieVon(id) == KATEGORIE
    }
}
