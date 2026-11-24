# Familienfreigabe: Sicherheitsgrenzen

Die lokale Einladungssimulation und das Berechtigungsmodell aktivieren keine Freigabe. PRODUKTIV_AKTIV bleibt false.

Vor Produktivbetrieb erforderlich:
- Authentifizierte, gerätegebundene Identitäten; keine vertrauenswürdigen IDs aus unvalidierten Client-Eingaben.
- Serverseitig atomare, einmalige Einladungsannahme mit vertrauenswürdiger Zeit und Widerrufsprüfung.
- Kryptografisch zufällige Einladungstoken; keine Dokumentdaten in Einladungslinks.
- E2EE mit geprüfter Schlüsselverteilung, Gerätewiderruf und Schlüsselrotation.
- Serverseitige Zugriffskontrolle für alle Objekte und Metadaten.
- Keine Freigabe ohne explizite Zustimmung des Eigentümers.
- Widerruf verhindert zukünftigen Zugriff, kann bereits exportierte Klartexte nicht zurückholen.
- Tests für konkurrierende Annahmen, Replay, Offline-Konflikte und fremde Archivgrenzen.

Kein Backend, keine Konten und keine kostenpflichtigen Dienste eingerichtet. Die lokale Simulation ist kein Sicherheitsnachweis für einen verteilten Betrieb.
