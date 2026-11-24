# DokumentenPilot: Modernisierung, Qualitätsverbesserung und Familienfreigabe

**Stand: 24. November 2026.** Entwicklungsbranch: `feature/qualitaet-design-familienfreigabe`; Review-Ziel: `test/sprachauswahl`. Dieses Dokument trennt tatsächlich implementierte Änderungen von konzeptionellen und ungeprüften Punkten. **Kein Release und keine produktive Familienfreigabe freigegeben.**

## 1. Ausgangslage und Ursachen

Basis `c149f095f25c0f43286526e6ef5431b8782ddc4d`, zusätzlich die vorgegebenen Commits `75a4790d4f8b59eb15c0678495fee8b4bbff2fce` und `696e7e7774844ee419363233088c79e913e3c086` berücksichtigt. Der Featurebranch lag zunächst genau zwei Commits vor `test/sprachauswahl`.

Quellcodeprüfung der Assistenten, von `UnterlagenErkennung`, `UnterlagenSuche`, `DokumentTypErkennung`, `AssistentDatum`, `AssistentSuche`, `AssistentOberflaeche`, `MappenOberflaeche`, `UnterlagenMappen`, `OcrHilfe`, `MainActivity` und Builddateien ergab:

- Mehrere voneinander abweichende Begriffstabellen und Teilstring-Suchen führten zu fehlender Äquivalenz bzw. falschen Dokumenttypen.
- `AssistentErkennung` bevorzugte die Gehaltsabrechnung; der freie Assistent stellte Mehrfachanfragen nicht zuverlässig getrennt zusammen.
- Monatsdeduplizierung unterschied früher bei Kontoauszügen keine getrennten Konten/Auszugsnummern.
- Abrechnungsmonat muss dem Dokument statt bloß dem Importdatum entnommen werden; OCR-Zeilenumbrüche und Umlauttransliteration waren relevante Fehlerquellen.
- Anforderungsschreiben mussten für alle Nachweisarten ausgeschlossen werden.
- Die bisherige Mappenspeicherung hatte einen unsicheren `copyTo(overwrite = true)`-Fallback.
- Fehlende Keystore-Schlüssel konnten trotz vorhandener verschlüsselter Dateien durch neue Schlüssel ersetzt werden; das birgt Risiko für die Wiederherstellung.

## 2. Tatsächlich vorgenommene Änderungen

- `SynonymKatalog.kt`: zentraler, modularer Katalog und begrenztes Matching; Normalisierung von Umlauten, OCR-Trennstrichen und ausgewählten Komposita.
- `UnterlagenErkennung.kt`, `DokumentTypErkennung.kt`, `AssistentSuche.kt`: Integration des Katalogs und unabhängige Auswertung mehrerer erkannter Dokumentanforderungen.
- `UnterlagenSuche.kt`: Ausschluss erkannter Anforderungsschreiben bei Nachweisen erweitert.
- `AssistentDatum.kt`: normalisierte Formulierungen wie „für“/„fuer“ sowie Perioden auf umgebrochenen OCR-Zeilen.
- Kontoauszüge mit gleichlautendem Monat lassen sich bei vorliegender IBAN oder Auszugsnummer getrennt berücksichtigen. Ohne zuverlässige Metadaten bleibt die Zusammenführung bewusst konservativ.
- `UnterlagenMappen.kt`: atomare JSON-Speicherung und Backup-Wiederherstellung. Bestehendes Datenformat unverändert.
- `MainActivity.kt`: Startsuche, Archivtitel/-zählung, untere Navigation, dynamische Kachelgestaltung, breitere Bildschirmunterstützung und dunkle Farbpalette. Individuelle Kategorien werden weiterhin aus derselben gespeicherten Liste angezeigt.
- `AssistentOberflaeche.kt`: gruppierte Ergebnisse pro Dokumentenanforderung mit fehlenden Nachweisen, Periodenhinweisen und Auswahl für vorhandene Mappen; dies ersetzt keine vollständige Unsicherheitsbewertung.
- `FamilienBerechtigungen.kt`: ausschließlich **deaktiviertes** und lokal prüfbares Berechtigungsmodell mit Vorlagen und Archiv-Eigentümergrenze.
- Neue UI-Texte in den Ressourcen für Deutsch, Englisch, Arabisch, Spanisch, Französisch, Polnisch, Russisch, Türkisch und Ukrainisch.
- Branch-spezifischer CI-Workflow; `versionCode = 4`, `versionName = 1.2.0-test`.

## 3. Synonymkatalog und Dokumentarten

Die fachlichen Familien sind im Map `SynonymKatalog.familien` definiert. Erweiterungen sind dort zentral möglich, ohne weitere handgepflegte Kopien. Die aktuellen Familien umfassen:

Gehaltsabrechnungen; Kontoauszüge; Personalausweis; Arbeitsvertrag; Mietvertrag; Steuererklärung; Steuerbescheid; Selbstauskunft; Lohnsteuerbescheinigung; Renteninformation; Rentenbescheid; Rentenversicherungsverlauf; Eigenkapitalnachweis; Kindergeldbescheid; Kreditvertrag; Bausparvertrag; Wohnflächenberechnung; Grundbuchauszug; Meldebescheinigung; Geburtsurkunde; Krankenversicherungsnachweis; Versicherungspolice; Arbeitszeugnis; Nebenkostenabrechnung; Wohnungsgeberbestätigung; Rechnung; Aufenthaltstitel; Führungszeugnis; Fahrzeugschein; Fahrzeugbrief; Führerschein; Arztbrief; Laborbefund; Arbeitsunfähigkeitsbescheinigung; Stromrechnung.

Die Kategoriekacheln sind **nicht** mit dieser Liste gleichzusetzen. Die bisherigen Standardkategorien sind weiterhin Allgemein, Arbeit, Bank & Kredite, Wohnen, Versicherungen, Familie & Behörden; individuell angelegte Kategorien bleiben gespeichert.

Explizit nicht gleichgesetzt: Renteninformation/Rentenbescheid/Versicherungsverlauf, Lohnsteuerbescheinigung/Gehaltsabrechnung, Zahlungsaufforderung/Rechnung, Arbeitszeugnis/Arbeitsvertrag, Baufinanzierungsanfrage/Kreditvertrag, Anforderungsschreiben/Kontoauszug. Begriffe werden **nicht** mit unkontrollierter Levenshtein-Distanz vermischt.

Die App hat die neun genannten Oberflächensprachen. Die fachliche Synonymerkennung ist bislang überwiegend deutsch und punktuell englisch. **Vollständige semantische Erkennung in allen neun Sprachen ist nicht implementiert.** ML Kit Latin OCR ersetzt keine arabische/kyrillische OCR-Prüfung.

## 4. Assistenten, Zeiträume, Checklisten und OCR

Eine zusammengesetzte Anfrage wird in erkannte Dokumentfamilien aufgespalten und für jede Art getrennt ausgewertet. Mengen und einige Jahresangaben werden zugeordnet, bei Gehaltsabrechnungen zählen nur unterschiedliche erkannte Abrechnungsmonate. Kontoauszüge werden bei erkennbarer IBAN/Auszugsnummer differenziert.

**Noch offen:** umfassende Von-/Bis-Zeiträume, sichere Unterscheidung Anzahl vs. Zeitfenster für alle Formulierungen, namentliche Person-/Arbeitgeberfilter, Ausschlussformulierungen, vollständige Trefferbegründungen und Unsicherheitsstufen, versionierte Dublettenerkennung ohne OCR-Metadaten und weitergehende Strukturierung, Bearbeitung und Nachvollziehbarkeit von Mehrfach-Anforderungsergebnissen. Hierfür sind weitere Tests und Implementierung nötig; eine Teilimplementierung darf nicht als abgeschlossene natürliche Spracherkennung gelten.

OCR wird bei Import und per manueller Nachverarbeitung lokal ausgeführt und verschlüsselt gespeichert, soweit im Bestand implementiert. Vollständige automatische Nachverarbeitung sämtlicher fehlerhafter OCR-Ergebnisse und aller Importformate ist noch nicht nachgewiesen. `OcrHilfe` verwendet während der Erkennung eine temporäre PDF-Datei im privaten App-Cache, die regulär im `finally` gelöscht wird. Der Crash-/Cache-Restfall muss gesondert sicherheitsgeprüft werden.

## 5. Tests: implementiert, ausgeführt und offen

Vorhanden waren Unit-Tests für UnterlagenErkennung, UnterlagenSuche, AssistentSuche, Gehaltsabrechnungsablauf und Synonymabgrenzungen. Ergänzt wurden `SynonymKatalogRegressionTest` und `FamilienBerechtigungenTest`, jeweils mit synthetischen Daten. Sie decken Synonymfamilien, Negativbeispiele, OCR-Umbruch, kombinierte Mengen, Steuerjahr, Anforderungen, Grundrechte, Rechteentzug, Eigentümergrenzen und automatische Freigabe ab.

CI-Workflow: [branch-qualitaet.yml](../.github/workflows/branch-qualitaet.yml). Auf dem Featurebranch läuft `gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --no-daemon --stacktrace` in GitHub Actions.

**Beobachtete Fehlversuche:** erster Workflow-Lauf scheiterte an der veralteten `setup-android`-Aktion (SDK-Paket `tools`); anschließend wurde diese Einrichtungsaktion ersetzt. Danach meldeten die Kotlin-Unit-Tests einen unzulässigen Sternimport aus einem `object` im neu hinzugefügten ACL-Test; dieser wurde durch explizite Importe korrigiert. Wegen aktivierter CI-Concurrency wurden mehrere zwischenzeitliche Läufe nach weiteren Commits abgebrochen. **Für den jeweils neuesten Commit ist der Status im Workflow zu prüfen; frühere Fehlversuche und abgebrochene Läufe sind kein Testnachweis.**

**Noch nicht ausgeführt/verifiziert:** Android-Instrumentierung und UI-Tests auf Emulator/Gerät; Migrationstest mit zuvor installierter signierter Altversion; Langzeit-/Performance-/Speicherlasttests; Keystore-/Datenwiederherstellung auf echten Geräten; Sicherheits-/Penetrationstest eines produktiven Backends; Offline-/Konflikttests über reale getrennte Geräte; alle Sprachvarianten mit realistischen synthetischen OCR-Bildern. Es wurden keine echten privaten Dokumente verwendet.

## 6. Benutzeroberfläche

Start- und Archivansicht verwenden weiterhin die bestehenden Activity-/View-Strukturen; es erfolgte bewusst keine riskante Komplettmigration auf Compose. Startsuche, responsive Kachelansichten, Kategorieanzahl, Navigation und Farbanpassungen wurden ergänzt. Alle gespeicherten Kategorien und die bisherigen Scan-, Import-, Assistenten- und Mappenaktionen bleiben über die bestehenden Wege erreichbar.

**Teilmodernisierung, kein vollständiges Material-Design-3-Redesign:** insbesondere die vollständige Assistenteninteraktion mit Vorschaukarten, native Material-3-Komponenten, Animationen, vollständige Accessibility-/Schriftgrößen-/RTL-/Tabletprüfung und alle dunklen Nebenansichten sind weiterhin offen.

Der Eintrag „Familie und Freigaben“ zeigt bewusst nur einen Sicherheitshinweis. **Keine Freigaben wurden aktiviert.**

## 7. Familienfreigabe – Architekturstatus

`FamilienBerechtigungen` enthält `Aktion`-Rechte, Vorlagen, vier Freigabearten (Archiv/Kategorie/Mappe/Dokument), Widerrufsmarkierung, Snapshot- bzw. automatische Neudokumentfreigabe sowie Kontrolle der Archiv-Eigentümergrenze. Sichtbare Dokumente können vor einer Suche mit der Policy gefiltert werden.

**Nur ein testbares Domänenmodell; KEIN Zugriffsschutz für echte Internetkonten.** `PRODUKTIV_AKTIV = false`. Es gibt noch keine Registrierung, Einladung, Identitätsprüfung, Gerätebindung, sichere Schlüsselfreigabe, unabhängige Serverautorisierung, Synchronisierung, Geräteübersicht, Aktivitätsprotokoll oder funktionierende Mehrgeräte-Familienansicht.

Vorgesehene Trennung:
1. Android-App: lokale Dokumente und Schlüssel, lokale Berechtigungsfilter, sichere Client-Kryptografie, Offline-Queue.
2. Transportadapter: austauschbare Upload-/Download-/Listen-/Delta-Schnittstelle; Server erhält nur Ciphertext und minimale technische IDs.
3. Authentifizierter Dienst: kontobezogene serverseitige Autorisierung bei jedem Request, transaktionale Versionierung, Wiederholungs- und Replay-Schutz, auditierbarer Widerruf.
4. E2EE-Schlüsselverwaltung: separater Archiv-/Dokumentenschlüssel, geprüfte Bibliothek, pro Gerät sicher eingewickelte Schlüssel, Rotation, Geräteentzug und Wiederherstellungsentscheidung.
5. Sicherheitstests einschließlich feindlich manipulierter Clients vor Freischaltung.

## 8. Berechtigungen, Bedrohungsmodell und Verschlüsselung

| Bedrohung | Pflichtschutz | Stand |
| --- | --- | --- |
| Zugriff auf fremde Familienarchive | Eigene Identität, Tenant-Grenze, Server- und Clientprüfung | Lokales Modell, Backend fehlt |
| Inhalts-/OCR-Leck beim Cloudanbieter | E2EE mit geprüfter Kryptografie, nie Klartext hochladen | Nicht implementiert; kein Upload |
| Leck über Suchtreffer, Miniaturen, Counts, Cache | Filter vor Suche/Rendering/Export, Metadaten minimieren | Für produktive Familie offen |
| Entzug einer Freigabe | Server sperrt zukünftige Zugriffe, Schlüsselrotation | Lokaler Widerruf modelliert, nicht produktiv |
| Bereits heruntergeladene Kopien | Kein rückwirkender technischer Entzug möglich; deutlicher Hinweis | In Produktkommunikation ergänzen |
| Geräteverlust oder Schlüsselverlust | Recovery-/Gerätelöschkonzept, Geräteschlüssel | Bestehende lokale KeyStore-Nutzung, Familien-Recovery offen |
| Gleichzeitige Änderungen | Versionen, Konfliktbehandlung, sichere Offline-Queue | Offen |
| Manipulierte Anfragen/Einladungen | Authentifizierung, Ablauf, einmalige Tokens, Server-ACL | Offen |
| Fehlerhafter lokaler Schreibvorgang | Atomare Speicherung | Mappen-JSON umgestellt |
| Fehlender lokaler Schlüssel | Nicht unbemerkt neu verschlüsseln | Fail-closed bei vorhandenem .dp-Archiv |

Bestehend: lokale **AES-GCM-Verschlüsselung über AndroidKeyStore** mit Alias `DokumentenPilotSchluessel`; IV-/Tag-Format unverändert. `android:allowBackup="false"` bleibt bestehen. Noch nicht behoben: lokal vorhandene Klartext-Dateinamen/-Kategorien in privaten App-Dateien/Einstellungen; diese dürfen nicht unverschlüsselt in eine zukünftige Cloud übertragen werden. Keine eigene Kryptografie implementieren.

Es liegt **keine belegbare Ende-zu-Ende-Verschlüsselung einer Familienfreigabe** vor. Ebenso wenig ist eine DSGVO-Konformität oder produktionstaugliche Zugriffskontrolle durch dieses Modell bewiesen.

## 9. Cloud-Optionen, Risiken und Kosten

| Option | Vorteile | Offene Punkte |
| --- | --- | --- |
| Selbstbetriebener EU-Dienst mit S3-kompatiblem Objektstore | Hohe Kontrolle über Region und Backend-/ACL-Logik | Hosting, Betrieb, Updates, Backups, Monitoring; laufende Kosten |
| EU-gehostetes Backend mit relationalen ACLs und Objektspeicher | Auth/Token-, Datenbank- und Storage-Bausteine verfügbar | DPA/AVV, präzise EU-Region, RLS/API-Härtung, Quoten, Abhängigkeit/Kosten |
| Eigenbetrieb mit Nextcloud/WebDAV und eigenem Autorisierungsdienst | Kontrollierbare Speicherebene | WebDAV allein löst weder Ende-zu-Ende-Verschlüsselung noch Dokument-ACLs oder Konfliktkontrolle |

**Keine Anbieterwahl getroffen, keine Cloud eingerichtet und keine kostenpflichtigen Dienste gestartet.** Konkrete Preise, Speicherklassen, EU-Regionen, DSGVO-Dokumentation und SLAs vor Entscheidung anhand eines realen Angebots prüfen. Ein anbieterneutraler Adapter einschließlich manipulationsresistenter Autorisierung muss erst implementiert und getestet werden.

## 10. APK, AAB, Versionierung und Upgrade

`applicationId = "de.dokumentenpilot.app"` und bisheriger Keystore-Alias bleiben gleich. `versionCode: 3 → 4`; `versionName: 1.2.0-test`; `minSdk = 26`; `targetSdk = 35` bis zur gesondert getesteten Play-Anpassung. Der Branch-Workflow versucht Debug-APK, unsignierte Release-APK und Release-AAB zu bauen; **nur erfolgreich hochgeladene GitHub-Actions-Artefakte gelten als verfügbar**.

Die Debug-APK ist mit dem Debugschlüssel signiert und kann **nicht** als In-Place-Update über eine anders signierte bestehende Produktions-App installiert werden. Eine unsignierte Release-APK ist erst **nach Signierung mit exakt dem bestehenden privaten Schlüssel** installierbar. Für ein Update sind identische Application-ID, kompatibles Signaturzertifikat und höherer VersionCode nötig.

Sicheres Prüfverfahren: Auf einem Emulator/isolierten Testgerät die alte APK mit ihrem passenden Test-/Original-Zertifikat installieren, synthetische verschlüsselte Dokumente, Mappen, Kategorien, Einstellungen und OCR erfassen, App ohne Deinstallation mit gleich signiertem neuem APK aktualisieren, Daten und Entschlüsselung prüfen, erst danach echten Geräteeinsatz erwägen. **Nie die produktive App deinstallieren, um einen Signaturfehler zu umgehen.** Eine echte Geräteinstallation ist nicht geprüft worden. Der private Signaturschlüssel darf nicht ins Repository und wird nicht angefordert.

## 11. Google Play – Stand 24.11.2026

Laut offizieller [Android-Play-Ziel-API-Anforderung](https://developer.android.com/google/play/requirements/target-sdk) müssen reguläre neue Apps/Updates seit 31.08.2026 mindestens **Android 16 / API 36** anvisieren; im Projekt steht weiterhin 35. **Damit ist ein neues Play-Update aktuell blockiert**, bis Migration und Tests erfolgt sind.

Vor der Veröffentlichung prüfen:
- [ ] `targetSdk >= 36` und dazugehörige Laufzeit-/Layout-/Berechtigungsänderungen auf mehreren API-Versionen.
- [ ] Signaturkompatibilität, AAB, Play App Signing und Bestandszertifikat: [Google-Anleitung](https://support.google.com/googleplay/android-developer/answer/9842756).
- [ ] Korrekt ausgefüllte [Datensicherheitserklärung](https://support.google.com/googleplay/android-developer/answer/10787469) anhand **tatsächlicher** SDKs, Netzkommunikation und Datenflüsse.
- [ ] DSGVO-/Datenschutzprüfung, aktuelle Datenschutzerklärung, Aufbewahrung, Auftragsverarbeiter, Kontakt.
- [ ] Bei späteren Nutzerkonten echte [Kontolöschung](https://support.google.com/googleplay/android-developer/answer/13327111), Server-/Geräte-/Backup-Datenlöschung und Web-Löschweg.
- [ ] Nutzung der Scanner-/OCR-SDKs und Play-Services-Berechtigungen prüfen.
- [ ] Übersetzte Store-Texte, Icon, Feature-Grafik, echte Screenshots ohne personenbezogene Daten.
- [ ] Internen Testtrack mit synthetischen Daten nutzen; erst danach manuelle Freigabe.

### Store-Entwurf Deutsch (noch nicht veröffentlichen)
**Kurz:** „Dokumente sicher ordnen, scannen und wiederfinden.“

**Lang:** „DokumentenPilot hilft dir, PDFs und gescannte Unterlagen auf deinem Android-Gerät übersichtlich zu verwalten. Ordne Dokumente Kategorien und Mappen zu, erkenne Text per OCR und suche nach passenden Nachweisen. Stelle Unterlagen für Checklisten zusammen und öffne oder exportiere Dokumente gezielt. Das lokale Archiv verwendet Android-Keystore-gestützte Verschlüsselung. Familienfreigaben über das Internet sind derzeit nicht verfügbar.“

### Store draft English (not for publication)
**Short:** “Scan, organize and find your documents.”

**Long:** “DokumentenPilot helps you manage PDFs and scanned documents on your Android device. Organize files into categories and folders, recognize text with OCR, search for supporting records, and assemble document checklists. Open or export selected files when needed. The local archive uses Android Keystore-backed encryption. Internet-based family sharing is not yet available.”

### Screenshot- und Grafik-Konzept
Synthetische Beispieldaten: Startseite mit Kategorien, Archiv mit Suche, Scan-/Importansicht, Assistenten-Checkliste, Mappenübersicht, Einstellungen. Mindestens Telefon im hellen und dunklen Modus; barrierefreie Texte und aktuelles Store-Format vor Upload prüfen. Kein reales Ausweisdokument, keine echten IBANs und keine echten Namen abbilden.

### Release Notes – Testbranch
„Verbesserte Synonym- und Dokumenttyp-Erkennung, getrennte Auswertung mehrerer Unterlagenanforderungen, robustere Mappenspeicherung, modernisierte Startseite und Navigation. Familienfreigabe bleibt aus Sicherheitsgründen deaktiviert. Vor produktiver Nutzung weitere Tests erforderlich.“

## 12. Owner-Entscheidungen, offene Arbeiten und Freigabe-Gates

1. Cloudanbieter bzw. eigener Server, EU-Region, zulässiges monatliches Budget, Datenaufbewahrung und Wartungsmodell.
2. Konto-/Geräteauthentifizierung, Identitätsprüfung für Einladungen, Freigabe-/Wiederherstellungs- und Widerrufsregeln.
3. E2EE-Schlüsseldesign unter Nutzung einer geprüften Bibliothek samt Recovery-Verfahren; externe Sicherheitsprüfung.
4. UX zu Rollen, automatischer Freigabe, privaten Dokumenten, Export und Einladungsfrist.
5. Synthetische Altversionsfixtures und signaturkompatible Emulator-Update-Tests, danach separater echter Eigentümertest.
6. API-36-Migration, Play-Sicherheits-/Datenschutzprüfung und Entscheidung über internen Testtrack.

**Release-/Freigabe-Gate:** erst dann als fertig bezeichnen, wenn CI grün, instrumentierte Migrations- und Kryptotests erfolgreich, Zugriffsschutz (auch OCR, Caches und Counter) getestet, Backend/Tokens sicher und Signatur-/Play-Anforderungen erfüllt sind. Bis dahin kein Merge, keine Veröffentlichung, keine produktive Familienfreigabe.
