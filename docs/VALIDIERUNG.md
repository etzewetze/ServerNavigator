# Validierung von ServerNavigator 1.1.0

## Automatisierte Prüfungen

JUnit-Testfälle decken die folgenden Verhaltensweisen ab. Maven schreibt Ergebnisse nach `target/surefire-reports/`; das Release enthält die im erfolgreichen Build erzeugten Berichte sowie Commit-ID und Laufzeitumgebung.

- Gemeinsamer Cooldown, unveränderte Wiederholungen, Aliase mit identischem Handler und sofortige automatische Verbindung ohne Mindestwartezeit.
- Gemeinsamer Vorgang bei mehreren gleichzeitigen Spielern, einschließlich paralleler Threads.
- Permission-Prüfung vor Start und Verbindung, Entzug während des Wartens sowie direkte Ziel-Zugriffskontrolle.
- Cancel, Ersetzung, unabhängiger Wechsel, Disconnect, Reload, Shutdown, Fristen und späte Antworten.
- Nicht erreichbare Backends ohne besonderen Startdienst/Startmeldung; begrenzte Netzwerk-Wiederholungen und korrektes Zurücksetzen des Fehlerzählers.
- Globale Standardzeit, konfigurierbare Überschreibung pro Server und Standardwert bei fehlender globaler Angabe.
- Kurzer Title mit Timer im echten Subtitle; separater Chattext in Titel- und Bossbar-Modus, deaktivierbar und je Server überschreibbar.
- Einmalige Chatbenachrichtigung trotz Ticks und Befehlscooldown-Wiederholungen.
- Alle Newline-Formen, mehrzeiliger Chat, Formatvererbung beim Aufteilen, einzeilige Bars und deutsche Platzhalter-Aliase.
- Abgelaufene Schätzung mit unveränderter Überschrift und verständlichem Ersatz für den Timer.
- Abweisung von mehr als zwei Titelzeilen, auch bei indirekt eingefügtem mehrzeiligem Status.
- Migration auf Format 2 mit exakter Sicherung, unveränderten Server-/Permission-/Befehlsdaten und sichtbaren neuen Anzeigevorlagen.
- Keine Speicherung bei ungültiger Alt-Konfiguration oder zwischenzeitlich geändertem Dateiinhalt.
- Keine fremden Befehlsüberschreibungen, Entfernen eigener alter Aliase und Rollback bei Registrierungsfehlern.

## Ausgeführte Prüfungen am 10. November 2026

- Build mit **Temurin 25.0.5+8**, **Apache Maven 3.9.11** und offizieller **Velocity API 4.3.0**.
- **56 Tests**, **0 Fehler**, **0 fehlgeschlagene Tests**, **0 übersprungen**; `clean verify` erfolgreich.
- 22 Produktions- und sechs Testdateien kompiliert; installierbare JAR und Sources-JAR gebaut. Metadaten, Format-2-Konfiguration und isolierter TOML-Parser geprüft; keine Velocity-/Adventure-Klassen mitgeliefert.
- Auf offizieller, anhand der SHA-256-Prüfsumme geprüfter **Velocity 4.3.2-SNAPSHOT Build 48** (`git-c80f44fb-b48`) mit fünf registrierten lokalen Testadressen erfolgreich geladen.
- Die tatsächliche Format-1-Beispieldatei aus Release 1.0.0 wurde beim Start erfolgreich migriert. Sicherungsdatei byteinhaltlich geprüft; Serverzeiten und Provider-Konvertierung kontrolliert. Neues Title-/Subtitle-Layout und einmaliger Chattext stehen editierbar in der migrierten Datei.
- Auf dem laufenden Proxy Status für alle fünf Ziele, gültigen Format-2-Reload, Wechsel der Modusauswahl zu BOSSBAR und Aliaswechsel `surv` → `sv` geprüft. Neuer Alias wurde erkannt, alter entfernt.
- Ein ungültiger dreizeiliger Title wurde beim Reload abgewiesen; die bisherigen Befehle und Konfiguration blieben aktiv. Proxy anschließend beendet.
- `git diff --check` ohne Befund; aktuelle Quellen und Dokumentation auf entfernte Anbieterbindungen geprüft.

Die Proxy-Prüfung nutzte ausschließlich lokale Testadressen ohne laufende Minecraft-Backends. Sie belegt keine echte Spieleraufnahme und keine gerenderte Anzeige im Spiel. Lokale Maven-Proxy-/Temporärpfade werden nicht mitgeliefert.

## Im tatsächlichen Netzwerk weiterhin erforderlich

- Echte Spielerwechsel und Verbleib auf dem bisherigen Backend während abgewiesener Loginversuche.
- Login zu einem zunächst nicht erreichbaren und anschließend unabhängig gestarteten Backend.
- Verhalten mit tatsächlichem Permission-Provider sowie anderen Proxy-Plugins; direkte `/server`-Versuche ohne Ziel-Permission.
- Kapazität, Whitelist, Wartungsplugins und Forwarding mit echten Ablehnungsnachrichten.
- Anzeige im Minecraft-Client bei unterschiedlichen GUI-Skalierungen, Bildschirmgrößen und optionalen Resourcepacks. Komponententests ersetzen keinen Blick auf das gerenderte Spielbild.
- Cancel und unabhängiger Wechsel während eines echten laufenden Logins. Die öffentliche API garantiert keine Rücknahme bereits zugelassener Logins.
- Optionaler HTTPS-POST mit eigenem Dienst einschließlich Authentifizierung, 2xx, Ablehnung und Timeout.

Ein isolierter Proxy mit registrierten Testadressen belegt das Laden des Plugins und Verwaltungsbefehle. Er ersetzt keine echten Backend-/Spieler- oder Client-Anzeigetests.
