# Geschlossener Zwei-Geräte-Test

Diese Seite beschreibt den ursprünglichen synthetischen Pilot. Der separate Debug-Modus für Archiv-PDFs ist im [Familien-Archivtest](familien-archivtest.md) dokumentiert.

Vorschlag: direkter Transport im gemeinsamen WLAN, ohne Cloudkonto. Keine produktive Freigabe. Nur fest eingebaute synthetische Testdaten; kein Zugriff auf Archiv, OCR, Mappen oder lokale Archivschlüssel.

## Implementierungsgrenzen

- QR-Einladung mit kurzlebiger, zufälliger Sitzungskennung und authentifiziertem öffentlichen Schlüssel bzw. Zertifikatsfingerabdruck; keine Dokumentdaten im QR-Code.
- Standard-TLS 1.3 mit expliziter Bindung an den bestätigten Gegenstellenschlüssel; gegenseitige Gerätebestätigung vor Datenübertragung. Keine Trust-all-Konfiguration, kein Klartext-Fallback.
- Einmalige Annahme, Ablauf mit monotoner lokaler Zeit, Replay-Abwehr, begrenzte Nachrichtenlänge und Verbindungsdauer.
- Nur fest definierte synthetische Nutzlast, keine Dateiauswahl, kein Archivadapter, keine Hintergrundübertragung.
- Widerruf beendet Sitzung und verhindert weitere Übertragungen; bereits empfangene Kopien sind nicht rückholbar.
- Schlüssel und Sitzungsdaten getrennt vom bestehenden Archiv. Keine Änderung an dessen Verschlüsselung oder Screenshot-Schutz.

## Freigabegates

Tests für falschen Schlüssel, abgelaufene Einladung, Replay, parallele Annahme, manipulierte Nutzlast, Abbruch, Netzwechsel und Widerruf. Android-Berechtigungen und Netzwerkverhalten prüfen. Zwei physische Geräte mit synthetischen Daten testen. PRODUKTIV_AKTIV bleibt false.

Der lokale WLAN-Pilot ist implementiert. Kein externer Server, kein Cloudanbieter und keine kostenpflichtigen Dienste. Internet-Synchronisierung und produktive Familienfreigaben bleiben außerhalb dieses Piloten.


## Abnahme der WLAN-Bindung (Protokollversion 5)

Nur synthetische Testdaten, beide Geräte mit demselben Build. Der reguläre Pilot verlangt ein konkretes aktives WLAN ohne VPN; die Gegenstelle muss direkt im privaten IPv4-Netz erreichbar sein. Die ausdrücklich markierte Transporttest-Schnittstelle ist nur für Emulatorprüfungen vorgesehen.

Vor einer Freigabe auf zwei physischen Geräten prüfen und protokollieren:

- Erfolgreicher Austausch nach Zustimmung auf beiden Geräten.
- Ablehnung auf jedem Gerät einzeln: kein Testdokument.
- WLAN ausschalten, zu Mobilfunk oder anderem WLAN wechseln, VPN einschalten oder IP wechseln: laufende Sitzung endet, keine weitere Übertragung; neue Einladung erforderlich.
- Wechsel während Einladung, Bestätigungsdialog und Übertragung getrennt prüfen.
- App sperren oder verlassen: Sitzung endet.
- Beide CI-Workflows müssen für denselben aktuellen Commit erfolgreich sein.

Die Unit-Tests prüfen die Netzwerkzustandsentscheidung, nicht die tatsächliche Zustellung von Android-Netzwerkcallbacks. Physische Abnahme bleibt erforderlich.

## Dauerhafte Geräteidentität und physische Abnahme

- Beide Geräte verwenden einen persistenten P-256-Signaturschlüssel im AndroidKeyStore. Die kurzlebige TLS-Serveridentität bleibt davon getrennt.
- Gegenseitige Signaturen binden Rollen, öffentliche Schlüssel, frische Zufallswerte und den TLS-Serverzertifikatsfingerabdruck. Dies ist kein TLS-Exporter.
- Vor Zustimmung die vollständigen Fingerabdrücke über Kreuz auf beiden Geräten persönlich vergleichen. Bei Abweichung abbrechen.
- Nach erfolgreicher Kopplung beide Apps neu starten und erneut verbinden; die Gerätefingerabdrücke müssen unverändert sein. Ein anderes Gerät muss abgewiesen werden.
- Zuletzt die dauerhafte Testsperre aktivieren und nach Neustart erneut eine Verbindung versuchen. Keine Wiederfreigabe innerhalb derselben Installation vorgesehen. Bereits empfangene Daten bleiben unberührt.
- Bei unterbrochener Zustimmung kann ein einseitig gespeicherter Vertrauenseintrag verbleiben; keine atomare geräteübergreifende Kopplung zugesichert.

CI-Nachweis für Code-Commit `f5fcf281eaf15c319508a164056222af3ffd49d8`: Branch-Qualitaet #381 und Android Update Smoke #306 erfolgreich, 19 Instrumentierungstests bestanden. Dieser Nachweis ersetzt weder die physische Abnahme noch eine unabhängige Sicherheitsprüfung. Keine produktive Freigabe.

## Statusanzeige und Empfangsbestätigung

- Beide Geräte müssen Protokollversion 5 verwenden. Der Sender meldet Erfolg erst nach der ausdrücklichen Empfangsbestätigung des Empfängers. Dieser prüft zuvor die synthetische Nutzlast und den aktiven Sitzungszustand.
- Nach erfolgreichem Austausch auf beiden Geräten die Familienübersicht erneut öffnen: lokal bestätigte Geräteidentität und keine aktive Sitzung erwarten. Die Anzeige ist kein Nachweis einer atomar beidseitig gespeicherten Kopplung.
- Bei Abbruch vor Empfangsbestätigung darf der Sender keinen erfolgreichen Empfang melden. Ein lokaler Vertrauenseintrag kann dennoch verbleiben.
- Nach Neustart muss die lokale Bestätigung erhalten bleiben; nach Sperre muss die Anzeige dauerhaft gesperrt lauten. Fehlerhafte gespeicherte Identität muss als unbekannt angezeigt werden.
- Keine privaten Dokumente für diese Abnahme verwenden. Die produktive Freigabe bleibt deaktiviert.


## Zielarchitektur nach dem WLAN-Pilot

Der WLAN-Zwang bleibt im Pilot unverändert. Für eine spätere Veröffentlichung sind Transport und Berechtigungsentscheidung getrennt zu halten. Internettransport (auch Mobilfunk) ist nicht implementiert und darf erst nach eigener Sicherheitsprüfung aktiviert werden.

Jedes Gerät behält sein eigenes verschlüsseltes Archiv. Eingehende und ausgehende Freigaben sind unabhängig, eigentümergebunden und pro bestätigtem Gerät zu prüfen. Private Ausnahmen haben Vorrang. Fremde Inhalte werden standardmäßig nicht dauerhaft gespeichert. Widerruf verhindert zukünftigen Zugriff, kann bereits exportierte Kopien jedoch nicht zurückholen.

Für ortsunabhängigen Zugriff sind authentisierte Ende-zu-Ende-Verbindungen mit optionaler Vermittlung zu untersuchen; ein Vermittlungsdienst darf keine Klartextdokumente erhalten. Erreichbarkeit bei ausgeschaltetem Eigentümergerät erfordert eine gesonderte verschlüsselte Speicherlösung. Keine kostenpflichtigen Dienste ohne ausdrückliche Zustimmung. Vor Veröffentlichung sind insbesondere Mehrgeräteverwaltung, gegenseitige Freigaben, Schlüsselwechsel, Widerruf, Rechteprüfung jeder Operation und Tests mit synthetischen Daten erforderlich.
