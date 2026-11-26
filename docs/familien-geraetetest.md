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
