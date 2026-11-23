# WSter89-
Repository name: DokumentenPilot Description: Android-App zur sicheren Dokumentenverwaltung Visibility: Private auswählen Add a README file: aktivieren

## Veröffentlichung bei Google Play

Gradle 8.11.1, JDK 17 und Android SDK 36 verwenden. Der Workflow „Android Release APK erstellen“ prüft Unit-Tests sowie Android Lint und erzeugt eine APK und ein Android App Bundle. In GitHub Actions kann der Workflow für den gewünschten Branch manuell gestartet werden.

```sh
gradle testDebugUnitTest lintRelease assembleRelease bundleRelease
```

Das AAB unter `app/build/outputs/bundle/release/` wird im nächsten Schritt mit dem Upload-Schlüssel signiert. Schlüsseldateien und Passwörter gehören weder ins Repository noch in die Build-Artefakte. Wenn bereits installierte APKs über Google Play aktualisierbar sein sollen, ist bei der Einrichtung von [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) der bisherige App-Signaturschlüssel für die Auslieferung zu verwenden. Der Upload-Schlüssel kann davon getrennt sein.

### Angaben für die Datenschutzerklärung und Datensicherheit

- DokumentenPilot speichert PDFs, OCR-Text, Dateinamen, Kategorien und Mappen verschlüsselt auf dem Gerät. Für das Archiv gibt es keine automatische Cloud-Sicherung oder Geräteübertragung.
- Die App verwendet Google ML Kit für Scanner und Texterkennung. Google dokumentiert die lokale Verarbeitung der Eingabedaten und nennt technische Daten für Diagnose und Nutzungsstatistik. Die [SDK-Datenerhebung](https://developers.google.com/ml-kit/android-data-disclosure) anhand der verwendeten Versionen für das Play-Formular prüfen.
- Bei einer vom Nutzer ausgelösten Freigabe erhält die gewählte App eine entschlüsselte Kopie. DokumentenPilot begrenzt die Lesefreigabe seiner temporären Kopien. Bereits beim Empfänger gespeicherte Kopien verbleiben beim Empfänger.
- Eine öffentliche Datenschutzerklärung mit Betreiber, Kontakt, Verarbeitung, Aufbewahrung und Löschung veröffentlichen. Die URL im Store-Eintrag und in der App verfügbar machen. Der Datenschutzhinweis in den App-Einstellungen beschreibt bereits die technischen Abläufe.

### Vor dem öffentlichen Release

1. Mit Testdokumenten eine bestehende, mit dem bisherigen App-Schlüssel signierte Installation aktualisieren. Dokumente, OCR-Index, Mappen und eigene Kategorien nach dem Update prüfen. Auch einen zweiten Start prüfen, nachdem die Metadaten migriert wurden.
2. Auf einem Gerät mit 16-KB-Seitengröße einen Scan, eine mehrseitige PDF-Vorschau und OCR ausführen. Die Play Console prüft zusätzlich das ausgelieferte Bundle.
3. App mit geöffneten Mappen- und Freigabedialogen in den Hintergrund schicken. Bei Rückkehr muss eine Authentifizierung nötig sein. Freigabe, Ablauf und das Aufräumen nach einem Neustart prüfen.
4. Das signierte AAB zuerst in einen internen Testtrack laden. Falls die Play Console für das Konto einen geschlossenen Test verlangt, dessen Vorgaben vor Produktionsfreigabe erfüllen.
