# Validierung

Maven-Berichte liegen nach dem Build unter `target/surefire-reports/`. Das Release enthält den im erfolgreichen Build erzeugten Bericht als ZIP sowie Commit-ID, Testanzahl und Laufzeitumgebung in `BUILD.txt`.

## Tatsächlich lokal ausgeführt am 9. November 2026

- `clean verify` mit **Eclipse Temurin 25.0.5+8** und **Apache Maven 3.9.11**: erfolgreich.
- **35 JUnit-Tests**, **0 Fehler**, **0 fehlgeschlagene Tests**, **0 übersprungen**.
- 18 Produktions- und vier Test-Quelldateien gegen die offizielle **Velocity API 4.3.0** kompiliert.
- Installierbare Fat-JAR und Sources-JAR erzeugt. JAR-Inhalt geprüft: Plugin-Metadaten und Beispielkonfiguration vorhanden, TOML-Parser isoliert, Velocity-/Adventure-Klassen nicht mitgeliefert.
- TOML, JSON und POM geparst; Workflow-Shellblöcke mit `bash -n` geprüft; `git diff --check` ohne Befund.
- Die Arbeitsumgebung benötigt einen eigenen temporären Pfad und einen Umgebungs-Proxy für Maven. Diese lokalen Pfade/Proxy-Einstellungen sind kein Bestandteil des Projekts und werden nicht ausgeliefert.

## Automatisiert abgedeckt

- Gemeinsamer Cooldown und identische Alias-Handler, unveränderte abgewiesene Eingaben.
- Ein Wake-Aufruf bei vielen Spielern, einschließlich paralleler Threads.
- Automatische Verbindung innerhalb des laufenden Befehlscooldowns.
- Kein Start ohne Permission, erneute Prüfung und Entwertung bei Entzug.
- Direkte Ziel-Zugriffskontrolle in der Kernlogik, verwendet vom Plugin-Event-Adapter.
- Cancel, Ersetzung, unabhängiger Wechsel, Disconnect, Reload, Shutdown und verspätete Antworten.
- ServerConnectedEvent vor Future-Abschluss ohne doppelte Folgeanmeldung.
- Abgelaufene Schätzung ohne negative Sekunden oder künstliche Freigabe.
- Begrenzte Wiederholungen, Fehlerklassifikation und sichere Backend-Textkomponenten.
- Beispielkonfiguration, unbekannte Ziele, Typen/Bereiche, Vererbung und Aliaskonflikte.
- Fremde Befehle bleiben erhalten, alte eigene Aliase verschwinden, Registrierungs-Rollback.

## Noch mit laufendem Netzwerk zu prüfen

1. Auf **Velocity 4.3.2 Build 48 / Temurin 25** laden. Kompilierung gegen API 4.3.0 ersetzt diesen Laufzeittest nicht.
2. AMP schlafen lassen, tatsächliche Startmeldung mit den Regex abgleichen; erst ein Spieler, dann mehrere gleichzeitige Wartende. Kein Spieler darf wegen AMP-Startablehnungen vom bisherigen Backend getrennt werden.
3. Bereites Ziel: sofortiger Wechsel, unabhängig von Cooldown und Startschätzung.
4. AMP-Timer: fünf Minuten nach letztem Spieler und zwei Minuten nach ungenutztem Wake. Abbruch beendet Prüfungen; kein Keepalive.
5. LuckPerms: ohne Ziel-Permission Alias und `/server <name>` testen, kein Wake. Permission während des Starts entziehen, keine automatische Folgeanmeldung.
6. Volles Backend, Whitelist, Wartungsplugin, Forwarding-Fehler, unerreichbarer Host und Portübergabe mit echten Ablehnungsgründen prüfen.
7. Cancel/Ersetzung/Disconnect direkt vor Login testen; bereits zugelassene Logins unterliegen der in README dokumentierten API-Grenze.
8. Alle Anzeigemodi im echten Client prüfen: Title-Fades, Farben, Chat-Intervalle, GUI-Skalierung und optionales Resourcepack.
9. Ungültiger Reload erhält Snapshot und Wünsche; gültiger Reload beendet alte Wünsche und ersetzt eigene Aliase. Keine konkurrierende Alias-Übernahme durch andere Plugins.
10. Optionalen HTTP-Provider mit eigener HTTPS-Brücke auf 2xx, 4xx/5xx, Token und Timeout prüfen. AMP-Standard benötigt diesen nicht.

Kein Zugriff auf das private Minecraft-/AMP-Netzwerk des Betreibers wurde vorausgesetzt. Simulierte Resultate sind keine echten Minecraft-Logins.
