# Familien-Archivtest

Der Archivtest ist in der Debug-APK unter **Meine Familie → WLAN-Archivtest starten** verfügbar. Auf dem empfangenden Gerät muss **Archivtest** gewählt werden. Beide Geräte müssen dieselbe Protokollversion ausführen, entsperrt sein und ein privates IPv4-WLAN nutzen. Der Fingerabdruckvergleich bleibt Teil jeder Einladung. Die Android-Keystore-Identität und die bestätigten Gegenstellen mit lokalen Namen bleiben über App-Neustarts erhalten. Eine Einladung und die zugehörigen Netzadressen gelten jeweils nur für eine Sitzung.

## Funktionsumfang

- Für jedes bestätigte Gerät verwaltet der Eigentümer eigene Regeln für das Standardarchiv, Mappen, Kategorien oder einzelne PDFs. Bei einer neuen Gruppenregel kann der Eigentümer neue passende Dokumente automatisch einbeziehen. Ohne diese Option gilt die ID-Liste zum Zeitpunkt der Freigabe. Ein privater Ausschluss hat Vorrang vor allen Regeln für dasselbe Gerät. Überlappende Regeln gelten unabhängig voneinander.
- Beide Geräte bieten einander ihre jeweils sichtbaren PDFs aus dem verschlüsselten Archiv an. Die Gegenstelle wählt pro Richtung und Sitzung eine Datei. Der Eigentümer prüft Identität, Sitzungszustand, aktuelle Zuordnung und Berechtigung vor und nach der Entschlüsselung. PDFs werden über das vorhandene gepinnte TLS 1.3 übertragen und in der App seitenweise angezeigt. Der Empfänger übernimmt die PDF weder in sein Archiv noch in einen externen Viewer.
- Die Sitzung kombiniert lokale Treffer mit berechtigten Titeln des jeweils verbundenen Geräts. Die Ansehen-Freigabe umfasst den angezeigten Dokumenttitel. Die Suchfreigabe steuert, ob die App den Titel in Trefferlisten verwendet. Für den Assistenten gibt es eine eigene Freigabe. Lokaler OCR-Text bleibt lokal. Der Assistent kann bei Remote-Dokumenten nur aus dem Titel ableiten, ob ein Treffer passt.
- Der Menüpunkt für das eigene Archiv verwendet die bestehenden Bearbeitungs-, Scan-, Import- und Verwaltungsabläufe. Eine automatische Freigabe kann so hinzugekommene Dokumente in der nächsten Sitzung erfassen. Die zusätzliche Freigabe **Umbenennen erlauben** gilt für sichtbare PDFs im ausgewählten Freigabeumfang. Vor dem Speichern prüft das Eigentümergerät Verbindung, aktuellen Titel, private Ausnahmen und beide Berechtigungen erneut. Das Umbenennen ändert den Titel im Archiv. **Verschieben erlauben** ermöglicht einen Kategorienwechsel. Beide Kategorien müssen das Ansehen und Verschieben für dieses Dokument zulassen. Das Eigentümergerät prüft die aktuelle Quellkategorie, die Zielkategorie und private Ausnahmen erneut. Mappenzuordnungen bleiben erhalten. **Löschen anfragen erlauben** erlaubt eine Anfrage für ein sichtbares Dokument. Dauerhaftes Löschen setzt die Zustimmung am Eigentümergerät innerhalb von 30 Sekunden voraus. Beim Bestätigen werden Freigaben, WLAN-Sitzung, Titel, Kategorie und private Ausnahmen erneut geprüft. Mit **PDFs einreichen erlauben** kann ein bestätigtes Gerät über **PDF ins Partnerarchiv einreichen** einen neuen Eintrag in einer freigegebenen Kategorie anlegen. Die Quelle liegt im eigenen Archiv und kann dort zuvor gescannt oder importiert werden. Die Einreichfreigabe kann ohne Leserecht gelten. Das Eigentümergerät prüft sie vor der Übertragung, nach Empfang und direkt vor dem verschlüsselten Speichern. Dieselbe PDF desselben Geräts in dieselbe Zielkategorie anzufordern erzeugt bei erneutem Versuch keinen zweiten Eintrag, solange der vorherige Eintrag existiert. Die Dateigröße ist auf 20 MiB begrenzt.
- Ein Widerruf über die Oberfläche beendet die lokale Sitzung. Die Vorschau der Gegenstelle benötigt jeweils nach etwa einer Sekunde eine erneute Bestätigung. Verbindungs- oder WLAN-Ausfall beendet den Zugriff bei der nächsten fehlgeschlagenen Bestätigung mit einem Lese-Timeout von drei Sekunden. Die Prüfung ist kein Rückruf bereits empfangener Daten gegenüber einem modifizierten Client.
- Der Test begrenzt eine PDF auf 20 MiB, die Vorschau auf 90 Sekunden und den Katalog auf 500 Einträge. Private Anforderungsschreiben aus der Mappenfunktion liegen außerhalb des Standardarchivs. Das bestehende Archivformat und der bestehende Schlüsselpfad werden weiterverwendet.

## Praktische Prüfungen mit synthetischen Unterlagen

1. Zwei aktuelle Android-Geräte verbinden und auf beiden Seiten die vollständigen Fingerabdrücke vergleichen. App neu starten und danach gespeicherte Kopplung und Namen prüfen. Auf einem dritten Gerät die zusätzliche Aufnahme auf beiden Seiten ausdrücklich auswählen. Einen einzelnen Peer widerrufen und die anderen Kopplungen erneut testen.
2. Je Gerät mehrere synthetische, auch mehrseitige PDFs importieren oder scannen. Je eine Mappe, Kategorie, Einzelfreigabe und Archivregel mit und ohne automatische Aufnahme anlegen. Ein privates Dokument über eine überlappende Regel ausschließen. Mit und ohne Umbenennen-Freigabe einen neuen Titel anfordern und einen gleichzeitig lokal geänderten Titel testen. Mit und ohne Freigabe für die Zielkategorie verschieben. Eine Löschanfrage auf dem Eigentümergerät ablehnen, auslaufen lassen und für eine ausdrücklich entbehrliche synthetische PDF bestätigen. In der App und nach Neustart prüfen, ob der Eintrag wie erwartet vorhanden oder gelöscht ist. Ein PDF in einer freigegebenen und danach in einer gesperrten Zielkategorie einreichen. Einen Scan zunächst im eigenen Archiv anlegen und dann einreichen. Dieselbe PDF zweimal einreichen, einen Abbruch vor der Übertragung und einen Widerruf während der Übertragung prüfen. Danach neue Dokumente hinzufügen, ein vorhandenes Dokument umbenennen, verschieben und löschen. Auf der Gegenstelle jedes Mal die sichtbaren Dokumente und Such- und Assistentenrechte prüfen.
3. In beiden Richtungen eine PDF ansehen. Während der offenen Vorschau die Regel entfernen, das Gerät sperren und den WLAN-Verlust jeweils getrennt auslösen. Vorschau, Bildschirmaufnahme, Screenshot, App-Wechsel, Prozessneustart und Fehlschlag im Hintergrund prüfen. Beide Geräte auf Mobilfunk oder ein anderes WLAN wechseln und kontrollieren, dass keine Verbindung zustande kommt.
4. Einen Update-Test mit derselben Signatur über einen älteren Build durchführen. Vorhandene synthetische Archivdateien danach öffnen und die bestehenden Scan-, Import-, OCR- und Verwaltungsabläufe prüfen. Ein anders signierter Debug-Build lässt sich wegen der Android-Signaturprüfung nicht als Update installieren.
5. Prüfen, dass eine Gegenstelle mit älterer Protokollversion, eine widerrufene Gegenstelle und ein Gerät ohne bestätigte Identität keine Archivdaten erhalten.

## Vorbereitung für späteren Internetbetrieb

Der Archivaustausch verarbeitet Ein- und Ausgabeströme einer bereits authentifizierten Sitzung. Regeln sind an Eigentümer und bestätigte Geräteschlüssel gebunden. Diese Trennung lässt sich für einen späteren Transport nutzen. Ein Internetbetrieb braucht eine gesonderte Ende-zu-Ende-Schlüsselverteilung, vertrauenswürdige Geräteaufnahme und Wiederherstellung, Schlüsselrotation, widerrufbare Sitzungen sowie Tests für Replay, Geräteverlust und konkurrierende Änderungen. Der Test verwendet ausschließlich den vorhandenen WLAN-Transport.

### OCR-Zeitüberschreitungen

Wenn die primäre Texterkennung ausfällt oder ihr Zeitlimit erreicht, nutzt die App für die betroffene und die folgenden Seiten desselben PDFs die installierten lokalen Deutsch- und Englischmodelle. Bei schwacher Erkennung prüft sie auch gedrehte Fassungen. Bereits erkannte Seiten bleiben im Textindex. Die ergänzenden arabischen und kyrillischen Modelle stehen weiterhin zur Verfügung. Auf Testgeräten sollten ein mehrseitiger synthetischer Scan und ein um 90° gedrehtes PDF geprüft werden.

## Familienübersicht und erneuter Zugriff (1.3.0-test)

Beide Geräte behalten die bisherige Kopplung und die lokalen Archivregeln.
Für einen erstmaligen Kontakt wird in **Familienmitglied hinzufügen** eine
Einladung erstellt. Elektronische Fingerabdrücke müssen weiterhin auf beiden
Seiten verglichen werden. Für bereits gekoppelte Geräte reicht **Mit mir
geteilt** und die Auswahl des Eigentümers. Das Eigentümergerät muss entsperrt
sein und seine Familienübersicht im Vordergrund haben. Unter **Einstellungen
und Sicherheit** lässt sich die Bereitstellung nach einem WLAN-Wechsel
erneut starten. Nach Ablauf eines zehnminütigen Fensters endet die aktive
Sitzung, und die App startet die Bereitstellung im selben WLAN erneut.

Die App findet die Geräte per DNS-SD im privaten WLAN. DNS-SD veröffentlicht
während der Bereitstellung die technische Gerätekennung und den kurzlebigen
TLS-Zertifikatsfingerabdruck im lokalen Netz. Dokumenttitel und Freigaben
werden erst übertragen, nachdem beide Seiten die bereits gespeicherten
Geräteschlüssel anhand einer frischen, TLS-gebundenen Signatur geprüft haben.
IP und Route müssen zum aktuellen privaten WLAN gehören. Beim Verlassen
der App, WLAN-Wechsel oder Ändern von Rechten wird die Verbindung geschlossen.
Beim Update bleiben der bestehende Keystore-Alias und die gespeicherten
Regeln erhalten. Die ersten Einladungen verwenden weiterhin den
bisherigen Nachrichtenablauf.

Die aktive Seite zeigt genau ein Partnerarchiv. Darin sind die Anzeigen nach
Kategorie, erlaubter Mappe und zuletzt in dieser App-Nutzung geöffneten
Dokumenten verfügbar. Suche, Filter und Suchbereich bleiben beim Wechsel
zwischen Liste und PDF während der Sitzung erhalten. Der Katalog wird nach
jedem geöffneten Dokument neu geladen. Die Suche und der Assistent verwenden
für fremde Dokumente nur separat freigegebene Titel. Mappennamen werden nur bei
einer wirksamen Mappenfreigabe übertragen. Freigaben sind im eigenen Archiv
über die Dokumentkarte, im Mappenmenü oder über **Mit Familie freigeben**
erreichbar. Mehrere Dokumente und Empfänger lassen sich gleichzeitig
auswählen. Die Regeln werden je Gerät gespeichert. Empfängerrechte auf direkte Scan-, Export-, Weitergabe- oder
Mappenverwaltungsaktionen werden erst angeboten, wenn sie vollständig
umgesetzt sind. Scans oder Importe werden wie bisher zuerst ins eigene
Archiv übernommen und können danach eingereicht werden.

### Offene Abnahme für 1.3.0-test

- Auf zwei Geräten mit dem Originalschlüssel ohne Deinstallation aktualisieren.
  Ein bestehendes synthetisches Dokument, den Gerätenamen und die Regeln
  nach dem Update erneut prüfen.
- Die Familienübersicht auf beiden Geräten öffnen. Ein Partnerarchiv schließen
  und ohne Einladung erneut öffnen. Danach mehrere PDFs nacheinander öffnen
  und überprüfen, dass der gewählte Filter und der Suchtext erhalten bleiben.
- Beide Apps neu starten und mit der gespeicherten Kopplung verbinden.
  Mit einem dritten bereits bestätigten Gerät unabhängige Rechte prüfen.
- Getrennt während Liste und Vorschau WLAN trennen, Eigentümergerät sperren
  sowie einzelne Freigaben widerrufen. Auch einen WLAN-Zugangspunkt mit
  Client-Isolation oder blockierter Multicast-Erkennung prüfen.
- Die Karten, Filterdialoge und PDF-Navigation bei großer Schrift, kleinen
  und großen Displays sowie im Hell- und Dunkelmodus kontrollieren.
- Noch offen bleiben eine geräteübergreifende Gesamtliste, gruppierte
  Personenprofile mit mehreren Geräten und Profilbild sowie eine
  erneute Verbindungsaufnahme im Hintergrund.

## Alltagstest 1.4.0-test

Die Familienübersicht öffnet zuerst die Geräteliste. Das gewählte Archiv hat
eigene Ansichten für **Alle freigegebenen Dokumente**, **Mappen**,
**Kategorien** und **Zuletzt geöffnet**. Mappen und Kategorien zählen die
berechtigten Dokumente. Ein Dokument erscheint je Ansicht einmal und kann in
mehreren seiner berechtigten Mappen vorkommen. In gezielten Mappen und
Kategorien erscheinen nur passende Dokumente dieses Eigentümers. Kategorie-
und Mappennamen werden nur im Rahmen einer dafür wirksamen Freigabe bekannt
gegeben. Die App hat derzeit flache Mappen und Kategorien. Gleichnamige
Mappen verschiedener Geräte liegen in getrennten Archiven.

Die PDF-Liste zeigt den Originaltitel, den Eigentümer als zweite Zeile und
ein Aktionsmenü. Export, Weitergabe und inhaltliche Bearbeitung werden im
Partnerarchiv nicht angeboten. Eine Löschanfrage benötigt die gewohnte
Bestätigung am Eigentümergerät. Der Zurück-Pfeil führt durch die
Archivansichten, ohne beim Wechsel von Ordnern, Kategorien oder Dokumenten den
Transport zu beenden. Bei einem neuen Katalog nach einer PDF-Vorschau bleiben
Suchbegriff, gewählte Ansicht und Scrollposition innerhalb der Sitzung erhalten.
Bei einem Verbindungsfehler versucht die App die Verbindung im unveränderten
WLAN einmal automatisch neu aufzubauen. Nach dem Sitzungsende verwirft sie die
temporären Ansichten. Technische Gerätekennungen erscheinen beim Koppeln und
unter **Geräteinformationen und Sicherheit**.

### Hintergrundzugriff

Unter **Meine Familie → Einstellungen und Sicherheit → Hintergrundzugriff**
zuerst die Geräte auswählen und dann den Dienst ausdrücklich starten. Die
Geräteauswahl ist standardmäßig leer. Die sichtbare WLAN-Benachrichtigung hat
eine Schaltfläche zum Beenden. Der Dienst läuft bis zu 30 Minuten im
ursprünglichen privaten WLAN, auch wenn die Aktivität oder der Bildschirm
geschlossen wird. Android-Energiesparregeln oder Hersteller können den
Netzzugriff im Ruhezustand unterbrechen. Der Dienst nutzt weder Wachhaltesperren
noch einen automatischen Start nach Neustart. Netzwerkwechsel oder Stoppen
beenden den Hintergrundzugriff. Eine neue Freigabe ist zum Start nötig.

Der Dienst ist schreibgeschützt. Jede Anfrage und jede Verlängerung der
PDF-Ansicht prüft die bestätigte Geräteidentität und die Hintergrundfreigabe.
Der Eigentümer prüft die Archivregeln einschließlich privater Ausnahmen vor
und nach der Entschlüsselung. Der unveränderte App-gebundene Archivschlüssel
wird nur für eine berechtigte Anfrage aus dem Android Keystore geladen.
Es gibt keinen Schlüsselwechsel, keine Änderung am lokalen Entsperren und keine
neue dauerhafte Klartextdatei. Änderungen an Freigaben schließen bestehende
WLAN-Sitzungen. Für neu gestartete Verbindungen gelten die aktuellen Rechte.

### Abnahme auf echten Geräten

- Über 1.3.0-test mit dem Originalschlüssel aktualisieren. Die vorhandene App
  und ihre Daten behalten. Synthetische Dokumente, Kopplung, Gerätenamen,
  private Ausnahmen und Freigaben erneut öffnen.
- Auf A Hintergrundzugriff ausschließlich für B erlauben und starten. A
  sperren, B öffnen und bei ausgeschaltetem Bildschirm mehrere PDFs ansehen.
  Ein zusätzlich gekoppeltes C darf dabei keine Archivdaten von A erhalten.
  Ausschalten ohne gestarteten Dienst muss den Zugriff beenden.
- Über die Benachrichtigung stoppen und während einer offenen Vorschau die
  Freigabe oder Kopplung widerrufen. Jeweils den Abbruch auf B prüfen.
  WLAN-Wechsel, Bildschirm-Ruhezustand, Akkusparmodus und Ablauf der Laufzeit
  getrennt prüfen. Screenshots und Bildschirmaufnahmen weiterhin blockieren.
- Eine PDF zwei freigegebenen Mappen zuordnen. Auf je einem Gerät gleichnamige
  Mappen anlegen, eine private Ausnahme setzen und ein Dokument nachträglich
  hinzufügen. In jeder Ansicht Eigentümer, Anzahl und Zuordnung prüfen.
- Über Gerät, Mappenübersicht, Mappe und PDF navigieren und den gesamten Weg
  zurückgehen. Zurück-Pfeil und Android-Geste getrennt prüfen. Dreipunktmenü,
  große Schrift und kleine sowie große Displays im Hell- und Dunkelmodus
  kontrollieren.
