package de.dokumentenpilot.app

/**
 * Fachlich äquivalente Dokumentbezeichnungen. Keine allgemeine Levenshtein-
 * oder Teilstring-Suche: insbesondere Bescheide, Auskünfte und Anforderungen
 * dürfen nicht mit den eigentlichen Nachweisen verwechselt werden.
 *
 * Für neue Begriffe nur die entsprechende fachliche Familie erweitern.
 * Die Anzeigenamen bleiben mit bestehenden Mappen/Checklisten kompatibel.
 */
object SynonymKatalog {
    val familien: Map<String, Set<String>> = linkedMapOf(
        "Gehaltsabrechnungen" to setOf(
            "gehaltsabrechnung", "gehaltsabrechnungen", "lohnabrechnung",
            "lohnabrechnungen", "verdienstabrechnung", "verdienstabrechnungen",
            "bezügeabrechnung", "bezügeabrechnungen", "entgeltabrechnung",
            "entgeltabrechnungen", "entgeltnachweis", "entgeltnachweise",
            "gehaltsnachweis", "gehaltsnachweise", "verdienstnachweis",
            "verdienstnachweise", "lohnnachweis", "lohnnachweise", "lohnzettel",
            "entgeltbescheinigung", "entgeltbescheinigungen",
            "payslip", "payslips", "salary slip", "salary statement"
        ),
        "Kontoauszüge" to setOf(
            "kontoauszug", "kontoauszüge", "kontoauszügen",
            "bankauszug", "bankauszüge", "bankauszügen",
            "bank statement", "bank statements"
        ),
        "Personalausweis" to setOf("personalausweis", "ausweiskopie", "identitätsnachweis", "identity card"),
        "Arbeitsvertrag" to setOf("arbeitsvertrag", "arbeitsverträge", "anstellungsvertrag",
            "anstellungsverträge", "beschäftigungsvertrag", "beschäftigungsverträge", "employment contract"),
        "Mietvertrag" to setOf("mietvertrag", "mietverträge", "mietvereinbarung",
            "mietvereinbarungen", "wohnungsmietvertrag", "rental agreement", "tenancy agreement"),
        "Steuererklärung" to setOf("steuererklärung", "steuererklärungen", "einkommensteuererklärung",
            "einkommensteuererklärungen", "tax return"),
        "Steuerbescheid" to setOf("steuerbescheid", "steuerbescheide",
            "einkommensteuerbescheid", "einkommensteuerbescheide", "tax assessment"),
        "Selbstauskunft" to setOf("selbstauskunft", "selbst auskunft"),
        "Lohnsteuerbescheinigung" to setOf("lohnsteuerbescheinigung", "jahreslohnsteuerbescheinigung",
            "elektronische lohnsteuerbescheinigung"),
        "Renteninformation" to setOf("renteninformation", "renteninformationen"),
        "Rentenbescheid" to setOf("rentenbescheid", "rentenbescheide",
            "rentenbewilligungsbescheid", "rentenanpassungsbescheid", "rentenfeststellungsbescheid", "pension award notice",
            "décision de pension",
            "resolución de pensión",
            "decyzja emerytalna",
            "решение о назначении пенсии",
            "emekli aylığı bağlama kararı",
            "рішення про призначення пенсії",
            "قرار المعاش"),
        "Rentenversicherungsverlauf" to setOf("rentenversicherungsverlauf", "versicherungsverlauf",
            "rentenversicherungsverläufe"),
        "Eigenkapitalnachweis" to setOf("eigenkapitalnachweis",
            "eigenkapitalnachweise", "nachweis des vorhandenen eigenkapitals"),
        "Kindergeldbescheid" to setOf("kindergeldbescheid", "kindergeldbescheide"),
        "Kreditvertrag" to setOf("kreditvertrag", "kreditverträge",
            "darlehensvertrag", "darlehensverträge", "loan agreement"),
        "Bausparvertrag" to setOf("bausparvertrag", "bausparverträge"),
        "Wohnflächenberechnung" to setOf("wohnflächenberechnung", "wohnflächenberechnungen",
            "living area calculation",
            "calcul de la surface habitable",
            "cálculo de superficie habitable",
            "obliczenie powierzchni mieszkalnej",
            "расчёт жилой площади",
            "yaşam alanı hesaplaması",
            "розрахунок житлової площі",
            "حساب المساحة السكنية"),
        "Grundbuchauszug" to setOf("grundbuchauszug", "grundbuchauszüge",
            "land register extract",
            "extrait du registre foncier",
            "extracto del registro de la propiedad",
            "odpis z księgi wieczystej",
            "выписка из земельного реестра",
            "tapu sicil kaydı örneği",
            "витяг із земельного реєстру",
            "مستخرج السجل العقاري"),
        "Meldebescheinigung" to setOf("meldebescheinigung", "meldebescheinigungen",
            "meldebestätigung", "meldebestätigungen",
            "einwohnermeldebescheinigung", "wohnsitzbescheinigung", "certificate of residence",
            "attestation de résidence",
            "certificado de empadronamiento",
            "zaświadczenie o zameldowaniu",
            "справка о регистрации по месту жительства",
            "ikametgah belgesi",
            "довідка про місце проживання",
            "شهادة الإقامة"),
        "Geburtsurkunde" to setOf("geburtsurkunde", "geburtsurkunden", "birth certificate",
            "geburtsbescheinigung",
            "acte de naissance",
            "certificado de nacimiento",
            "akt urodzenia",
            "свидетельство о рождении",
            "doğum belgesi",
            "свідоцтво про народження",
            "شهادة الميلاد"),
        "Krankenversicherungsnachweis" to setOf("krankenversicherungsnachweis",
            "krankenversicherungsbescheinigung", "versicherungsnachweis krankenversicherung", "private krankenversicherung"),
        "Versicherungspolice" to setOf("versicherungspolice", "versicherungspolicen",
            "versicherungsschein", "versicherungsscheine", "insurance policy"),
        "Arbeitszeugnis" to setOf("arbeitszeugnis", "arbeitszeugnisse", "arbeitsreferenz"),
        "Nebenkostenabrechnung" to setOf("nebenkostenabrechnung", "nebenkostenabrechnungen",
            "betriebskostenabrechnung", "betriebskostenabrechnungen",
            "heizkostenabrechnung", "mietnebenkostenabrechnung", "service charge statement",
            "décompte des charges locatives",
            "liquidación de gastos de alquiler",
            "rozliczenie kosztów eksploatacyjnych",
            "расчёт эксплуатационных расходов",
            "yan gider hesap dökümü",
            "розрахунок експлуатаційних витрат",
            "كشف المصاريف الإضافية"),
        "Wohnungsgeberbestätigung" to setOf("wohnungsgeberbestätigung", "wohnungsgeberbestätigungen"),
        "Rechnung" to setOf("rechnung", "rechnungen", "invoice", "invoices"),
        "Aufenthaltstitel" to setOf("aufenthaltstitel", "aufenthaltserlaubnis"),
        "Führungszeugnis" to setOf("führungszeugnis", "polizeiliches führungszeugnis"),
        "Fahrzeugschein" to setOf("fahrzeugschein", "zulassungsbescheinigung teil i"),
        "Fahrzeugbrief" to setOf("fahrzeugbrief", "zulassungsbescheinigung teil ii"),
        "Führerschein" to setOf("führerschein", "fahrerlaubnis", "driving licence"),
        "Arztbrief" to setOf("arztbrief", "arztbriefe", "ärztlicher bericht"),
        "Laborbefund" to setOf("laborbefund", "laborbefunde", "laborbericht"),
        "Arbeitsunfähigkeitsbescheinigung" to setOf("arbeitsunfähigkeitsbescheinigung",
            "arbeitsunfähigkeitsbescheinigungen", "krankmeldung", "au bescheinigung"),
        "Darlehensvermittlungsauftrag" to setOf("darlehensvermittlungsauftrag", "kreditvermittlungsauftrag"),
        "Datenschutzbestätigung" to setOf("datenschutzbestätigung", "bestätigung zum datenschutz und unterlagenerhalt"),
        "Steuer-Identifikationsnummer" to setOf("steuer-identifikationsnummer", "steueridentifikationsnummer", "steuer-id", "tax identification number", "steuer – identifikationsnummer", "steuer identifikationsnummer"),
        "Jahresabschluss" to setOf("jahresabschluss", "jahresabschlüsse", "annual financial statements"),
        "Betriebswirtschaftliche Auswertung" to setOf("betriebswirtschaftliche auswertung", "bwa"),
        "Summen- und Saldenliste" to setOf("summen- und saldenliste", "summen- und saldenlisten"),
        "Vermögensübersicht" to setOf("vermögensübersicht", "vermögensaufstellung"),
        "Saldenbestätigung" to setOf("saldenbestätigung", "restschuldbestätigung"),
        "Unterhaltsnachweis" to setOf("unterhaltsnachweis", "nachweise zu unterhaltsverpflichtungen", "nachweise zur unterhaltsberechtigung", "nachweise über unterhaltsverpflichtungen", "unterhaltsberechtigung"),
        "Lageplan" to setOf("lageplan", "flurkarte", "liegenschaftskarte", "auszug aus dem liegenschaftskataster"),
        "Kaufvertragsentwurf" to setOf("kaufvertragsentwurf", "entwurf des notariellen kaufvertrags", "entwurf notarieller kaufvertrag", "entwurf des notariellen kaufvertrages"),
        "Notarieller Kaufvertrag" to setOf("notarieller kaufvertrag", "notariellen kaufvertrag"),
        "Gesamtkostenaufstellung" to setOf("gesamtkostenaufstellung", "baukostenaufstellung"),
        "Bauzeichnungen" to setOf("bauzeichnungen", "grundrisse", "gebäudeschnitte", "gebäudeansichten"),
        "Verbraucherbauvertrag" to setOf("verbraucherbauvertrag"),
        "Feuerrohbauversicherung" to setOf("feuerrohbauversicherung"),
        "Baugenehmigung" to setOf("baugenehmigung", "building permit",
            "permis de construire",
            "licencia de obras",
            "pozwolenie na budowę",
            "разрешение на строительство",
            "yapı ruhsatı",
            "дозвіл на будівництво",
            "رخصة البناء"),
        "Exposé" to setOf("exposé", "immobilienexposé", "exposee"),
        "Objektfotos" to setOf("objektfotos", "innenaufnahmen", "außenaufnahmen"),
        "Gebäudeversicherung" to setOf("gebäudeversicherung", "wohngebäudeversicherung"),
        "Bürgergeldbescheid" to setOf("bürgergeldbescheid", "bürgergeld", "bürgegeld",
            "bürgergeldbewilligungsbescheid", "bürgergeldbescheide",
            "German citizen's benefit award notice",
            "notification d'attribution du Bürgergeld",
            "resolución de concesión del Bürgergeld",
            "decyzja o przyznaniu Bürgergeld",
            "решение о назначении Bürgergeld",
            "Bürgergeld yardım kararı",
            "рішення про призначення Bürgergeld",
            "قرار منح إعانة Bürgergeld"),
        "Grundrentenbescheid" to setOf("grundrentenbescheid", "grundrente", "grundrentenzuschlag",
            "bescheid über grundrentenzuschlag",
            "German basic pension supplement notice",
            "notification de majoration Grundrente",
            "resolución del complemento Grundrente",
            "decyzja o dodatku Grundrente",
            "решение о надбавке Grundrente",
            "Grundrente ek ödeme kararı",
            "рішення про доплату Grundrente",
            "قرار إضافة Grundrente"),
        "Energieausweis" to setOf("energieausweis", "energieausweise", "energiebedarfsausweis",
            "energieverbrauchsausweis", "bedarfsausweis", "verbrauchsausweis", "energy performance certificate",
            "diagnostic de performance énergétique",
            "certificado de eficiencia energética",
            "świadectwo charakterystyki energetycznej",
            "энергетический паспорт здания",
            "enerji kimlik belgesi",
            "енергетичний сертифікат будівлі",
            "شهادة كفاءة الطاقة"),
        "Stromrechnung" to setOf("stromrechnung", "stromabrechnung", "stromkostenabrechnung",
            "stromjahresabrechnung", "stromkostenrechnung", "elektrizitätsrechnung", "electricity bill",
            "facture d'électricité",
            "factura de electricidad",
            "rachunek za prąd",
            "счёт за электроэнергию",
            "elektrik faturası",
            "рахунок за електроенергію",
            "فاتورة الكهرباء")
    )

    /** OCR-Zeilenumbrüche, Umlautvarianten und getrennte Komposita normalisieren. */
    fun normalisieren(text: String): String =
        text.lowercase(java.util.Locale.ROOT)
            .replace("\u00ad", "")
            .replace(Regex("""(?<=\p{L})-\h*\R\s*(?=\p{L})"""), "")
            .replace(Regex("""(?<=\p{L})\s+\R\s*(?=abrechnung\b)"""), "")
            .replace(Regex("""(?<=\p{L})[ _-]+(?=(?:abrechnung(?:en)?|vereinbarung(?:en)?|vertraege?|vertraegen?|vertraege|vertrag|auszuege?|auszug|bescheide?|bescheid|nachweise?|nachweis)\b)"""), "")
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("""(?<=\p{L})[ _-]+(?=(?:abrechnung(?:en)?|vereinbarung(?:en)?|vertraege?|vertrag|auszuege?|auszug|bescheide?|bescheid|nachweise?|nachweis)\b)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun muster(begriff: String): Regex {
        val teile = normalisieren(begriff).split(' ').filter { it.isNotBlank() }
        val pattern = teile.joinToString("""[\s_-]+""") { Regex.escape(it) }
        return Regex("""(?<![\p{L}\p{N}])$pattern(?![\p{L}\p{N}])""")
    }

    private val musterNachArt: Map<String, List<Regex>> =
        familien.mapValues { (_, woerter) -> woerter.map(::muster) }

    fun passt(art: String, text: String): Boolean {
        val normal = normalisieren(text)
        if (art == "Notarieller Kaufvertrag" && musterNachArt["Kaufvertragsentwurf"]?.any { it.containsMatchIn(normal) } == true) return false
        return musterNachArt[art]?.any { it.containsMatchIn(normal) } ?: false
    }

    fun fundstellen(text: String): List<Pair<String, IntRange>> {
        val normal = normalisieren(text)
        return musterNachArt.flatMap { (art, muster) ->
            muster.flatMap { regex -> regex.findAll(normal).map { art to it.range }.toList() }
        }.sortedWith(compareBy<Pair<String, IntRange>> { it.second.first }
            .thenByDescending { it.second.last - it.second.first })
            .fold(mutableListOf<Pair<String, IntRange>>()) { result, treffer ->
                // Überlappende Synonyme derselben Art nicht doppelt zählen.
                if (result.none { it.first == treffer.first && it.second.first == treffer.second.first }) {
                    result.add(treffer)
                }
                result
            }
    }

    fun erkannteArten(text: String): List<String> = fundstellen(text).map { it.first }.distinct()

    fun synonyme(wort: String): Set<String> {
        val normal = normalisieren(wort)
        val familie = familien.entries.firstOrNull { (_, begriffe) ->
            begriffe.any { normalisieren(it) == normal }
        } ?: return setOf(wort)
        return familie.value
    }
}
