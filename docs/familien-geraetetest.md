# Geschlossener Zwei-Geräte-Test

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

Noch keine Transportimplementierung, keine Anbieterwahl, kein Server und keine Kosten. Internet-Synchronisierung und dauerhafte Familienidentitäten benötigen ein gesondertes Sicherheitsdesign.


## Abnahme der WLAN-Bindung (Protokollversion 2)

Nur synthetische Testdaten, beide Geräte mit demselben Build. Der reguläre Pilot verlangt ein konkretes aktives WLAN ohne VPN; die Gegenstelle muss direkt im privaten IPv4-Netz erreichbar sein. Die ausdrücklich markierte Transporttest-Schnittstelle ist nur für Emulatorprüfungen vorgesehen.

Vor einer Freigabe auf zwei physischen Geräten prüfen und protokollieren:

- Erfolgreicher Austausch nach Zustimmung auf beiden Geräten.
- Ablehnung auf jedem Gerät einzeln: kein Testdokument.
- WLAN ausschalten, zu Mobilfunk oder anderem WLAN wechseln, VPN einschalten oder IP wechseln: laufende Sitzung endet, keine weitere Übertragung; neue Einladung erforderlich.
- Wechsel während Einladung, Bestätigungsdialog und Übertragung getrennt prüfen.
- App sperren oder verlassen: Sitzung endet.
- Beide CI-Workflows müssen für denselben aktuellen Commit erfolgreich sein.

Die Unit-Tests prüfen die Netzwerkzustandsentscheidung, nicht die tatsächliche Zustellung von Android-Netzwerkcallbacks. Physische Abnahme bleibt erforderlich.
