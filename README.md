# ServerNavigator

Unabhängiges Velocity-Plugin für einfache Serverwechsel, gemeinsame Wartelisten und konfigurierbare Adventure-Anzeigen. Es funktioniert mit dauerhaft laufenden Backends, manuell gestarteten Servern und optionalen externen Startdiensten. Die Plugin-JAR gehört ausschließlich auf den Proxy.

Zielumgebung: **Velocity 4.3.2 Build 48 / Temurin 25**. Offizielle **Velocity API 4.3.0** und Adventure; keine Proxy-Interna. Die Java-Version der Backendserver ist für dieses Proxy-Plugin unerheblich.

## Installation und Update auf 1.1.0

1. [ServerNavigator-1.1.0.jar](https://github.com/etzewetze/ServerNavigator/releases/download/v1.1.0/ServerNavigator-1.1.0.jar) herunterladen.
2. Proxy stoppen, alte Plugin-JAR entfernen und neue JAR in `plugins/` legen. Proxy neu starten. Ein Konfigurations-Reload tauscht keine laufende JAR aus.
3. Alle konfigurierten Ziele müssen in `velocity.toml` unter `[servers]` registriert sein. Beispiel mit **anzupassenden** Adressen:

   ```toml
   [servers]
   lobby = "127.0.0.1:25566"
   challenges = "127.0.0.1:25567"
   survival = "127.0.0.1:25568"
   creative = "127.0.0.1:25569"
   testserver = "127.0.0.1:25570"
   try = ["lobby"]
   ```

4. `plugins/servernavigator/config.toml` bearbeiten und `/servernavigator reload` verwenden.

Eine neue Installation erhält die [vollständig kommentierte Beispielkonfiguration](src/main/resources/config.toml). Eine bestehende Datei aus Version 1.0 wird **nach erfolgreicher Validierung und Befehlsregistrierung** auf Format 2 umgestellt. Vorher entsteht daneben `config-v1-*.backup.toml`. Eigene Server, Befehle, Aliase, Permissions, Zeiten und HTTP-Trigger bleiben erhalten. Die bisherigen Anzeigevorlagen werden durch das neue kompakte Layout ersetzt; eine eigene Auswahl einzelner Anzeigemodi bleibt erhalten. Die frühere Standardkombination dreier Anzeigen wird zu `TITLE`. Alte Kommentare bleiben in der Sicherung; die neue Datei verweist auf das kommentierte Beispiel.

Ein bisheriger Standardwert von drei Netzwerk-Wiederholungen wird auf 100 angehoben; abweichende eigene Werte bleiben erhalten. Alte Startprovider werden auf normale Verbindungsversuche vereinheitlicht. Bei ungültiger Datei oder fremden Befehlskonflikten findet keine Dateiumstellung statt. Bei einem gleichzeitigen externen Dateiedit wird die geänderte Datei nicht überschrieben.

## Wartezeit: Standard und Überschreibung je Server

```toml
[settings]
expected-startup-seconds = 30
max-wait-seconds = 180

[servers.testserver]
velocity-name = "testserver"
display-name = "Testserver"
command = "testserver"
aliases = ["test"]
expected-startup-seconds = 20
```

Hier zeigt Testserver eine 20-Sekunden-Schätzung. Ohne dessen `expected-startup-seconds` gilt der globale Standard von 30 Sekunden. Auch der globale Wert ist optional; ohne ihn gilt 30. Weitere Server benötigen keine Codeänderung.

**Die Wartezeit ist eine Countdown-Schätzung.** Ein aufnahmebereiter Server wird sofort betreten, auch vor Ablauf der Schätzung und innerhalb des Befehlscooldowns. Der Countdown allein löst keine Verbindung aus. Läuft er ab, erscheint „Start dauert länger …“; keine negativen Sekunden. `max-wait-seconds` begrenzt unabhängig davon die tatsächliche Dauer eines Wunsches.

Später hinzukommende Spieler sehen die verbleibende Schätzung desselben gemeinsamen Vorgangs. Ihre eigene maximale Wartefrist beginnt mit ihrem Wunsch.

## Kompakte Anzeige und einmaliger Chattext

Standardansicht:

| Stelle | Standardtext |
| --- | --- |
| Titel, obere große Zeile | **Testserver Startet** |
| Untertitel, kleinere Zeile darunter | **~23 Sekunden** |
| Chat, einmal je angenommenem Wunsch | Sobald der Server antwortet, wirst du teleportiert. |

Für Titel oder ausschließlich Bossbar wählen:

```toml
[display]
modes = ["TITLE"]
# Alternativ: modes = ["BOSSBAR"]
wait-chat-enabled = true
wait-chat = '<gray>Sobald der Server antwortet, wirst du teleportiert.</gray>'
title = '<server> <gold>Startet</gold>'
subtitle = '<gray>~<seconds> Sekunden</gray>'
bossbar = '<server> startet <gray>| ~<seconds> s</gray>'
```

Der Chattext ist **unabhängig von der Modusauswahl**. Für beide Varianten kommt er einmalig; `CHAT` muss dafür nicht aktiviert sein. Abgewiesene Wiederholungen und Anzeige-Updates schicken ihn nicht erneut. `wait-chat-enabled = false` oder ein leerer `wait-chat` unterdrücken ihn.

Alle Anzeigeeinstellungen lassen sich einzeln unter `[servers.<id>.display]` überschreiben. Beispielsweise kann Survival nur die Bossbar und Testserver nur den Titel verwenden. Weiterhin kombinierbar sind `BOSSBAR`, `TITLE`, `ACTIONBAR` und `CHAT`; `NONE` steht allein. `CHAT` bedeutet zusätzliche wiederholte Statusmeldungen im `chat-interval-seconds`-Abstand. `NONE` unterdrückt laufende Anzeigen; die separat aktivierte einmalige Erklärung bleibt möglich.

Bossbar-Farbe und Unterteilung werden separat konfiguriert. Der Balken zeigt den verbleibenden Anteil der Schätzung, keinen gemessenen Boot-Fortschritt. Title-Einblendzeiten, Update-Intervall und alle Texte sind veränderbar.

### Newline und Platzhalter

Unterstützt werden **`<newline>`, `<newline/>`, `<br>`, echte Zeilenumbrüche und `\n`** in Konfigurationstexten. Beispiel:

```toml
[display]
title = '<server> Startet<newline/>~<zeit> Sekunden'
wait-chat = 'Sobald der Server antwortet, wirst du teleportiert.<newline/>Mit /servernavigator cancel kannst du abbrechen.'
```

Ein Umbruch im Titel wird auf die zwei tatsächlich vorhandenen Vanilla-Zeilen verteilt. Die zweite Inline-Zeile ersetzt dann die Vorlage `subtitle`. Alternativ `title` und `subtitle` getrennt konfigurieren; dies ist der Standard. Mehr als zwei Titelzeilen beziehungsweise mehr als eine explizite Untertitelzeile werden bei der Validierung abgelehnt.

Chat unterstützt mehrere Zeilen. Bossbar und Actionbar sind technisch einzeilig; dort verbindet das Plugin Zeilenumbrüche mit Leerzeichen. Farben, Verläufe, Schriftarten und Textformatierung bleiben beim Aufteilen erhalten. Vanilla bietet keine frei skalierbare Title-Schriftgröße. Die mitgelieferte Textdarstellung benötigt kein Resourcepack; eigene Fonts/Glyphen können später über `<font:namespace:key>...</font>` eingesetzt werden.

| Platzhalter | Inhalt |
| --- | --- |
| `<server>`, `<servername>` | konfigurierter Anzeigename |
| `<seconds>`, `<zeit>` | geschätzte verbleibende Sekunden, nie negativ |
| `<elapsed>` | Sekunden seit Beginn des gemeinsamen Vorgangs |
| `<cooldown>` | verbleibender Befehlscooldown |
| `<status>` | konfigurierter Status; innerhalb `status-*` selbst leer |
| `<reason>` | originale Ablehnungsnachricht |
| `<count>`, `<waiters>`, `<attempts>` | Wartende bzw. Versuche des betreffenden Spielers |

MiniMessage-Tags müssen geschlossen sein; Platzhalter und Newline-Tags brauchen keine End-Tags. Dynamische Fremdtexte werden als Komponenten oder unformatierte Werte eingefügt, nicht als zusätzliche MiniMessage-Tags ausgewertet.

## Verbindung und optionale Starttrigger

Ohne `wake`-Block gilt **`CONNECTION`**: eine normale asynchrone Velocity-Anmeldung. Ein bereiter Server nimmt den Spieler direkt auf. Ein nicht erreichbarer Server wird bis zu den konfigurierten Grenzen erneut geprüft. Das benötigt keinen bestimmten Hoster, kein Panel und keine spezielle Startmeldung. Währenddessen bleibt der Spieler auf seinem bisherigen Backend.

Ein Verbindungsversuch startet allein keinen Java-Prozess. Optional kann ein vorhandener externer Dienst auf Verbindungsversuche reagieren. Ein unabhängig gestartetes Backend wird ebenfalls automatisch betreten, sobald der echte Login gelingt.

Alternativ ist ein allgemeiner HTTPS-POST-Trigger vollständig implementiert:

```toml
[servers.testserver.wake]
type = "HTTP"
url = "https://START-SERVICE-EXAMPLE.invalid/wake/testserver"
token-environment = "SERVERNAVIGATOR_WAKE_TOKEN"
body = '{"server":"testserver"}'
```

Die `.invalid`-URL ist ein absichtlich ungültiger Platzhalter. Eine echte HTTPS-URL zum eigenen Startdienst einsetzen. Der optionale Bearer-Token stammt aus der Umgebung des Proxys; der JSON-Body wird wörtlich gesendet. Nur 2xx bestätigt den Trigger, keine Redirects, keine POST-Wiederholung. Das ist kein angenommener oder eingebauter Panel-Endpunkt.

`WakeupProvider` wird einmal je gemeinsamem Vorgang aufgerufen. Zusätzliche Spieler hängen sich an die Warteliste. `ReadinessChecker` verwendet echte asynchrone Logins: ein erfolgreicher Login verbindet den ersten Spieler und gibt danach die weiteren frei. Pings/offene TCP-Ports gelten nicht als Aufnahmebereitschaft. Es gibt keine Fake-Spieler und kein dauerhaftes Polling ohne Wünsche. Reale Folge-Logins können naturgemäß ebenfalls einen externen Wake-Dienst erreichen.

Unbekannte echte Backend-Ablehnungen, Whitelist, volle Server, Wartung und Authentifizierungsfehler beenden den jeweiligen Wunsch mit verständlichem Originalgrund. Optionale `starting-patterns` erkennen allgemeine Startmeldungen; `terminal-patterns` haben Vorrang. Netzwerkfehler beweisen keinen Schlaf- oder Startzustand. Sie werden durch `network-retries`, `max-connection-attempts` und `max-wait-seconds` begrenzt; `network-retries = 0` beendet beim ersten Netzwerkfehler. Startantworten setzen den Zähler aufeinanderfolgender Netzwerkfehler zurück.

## Befehle, Permissions und Cooldown

| Ziel | Hauptbefehl | Aliase |
| --- | --- | --- |
| lobby | `/lobby` | `/l`, `/hub` |
| challenges | `/challenges` | `/challenge`, `/ch` |
| survival | `/survival` | `/surv` |
| creative | `/creative` | `/cre` |
| testserver | `/testserver` | `/test` |

| Verwaltung | Berechtigung |
| --- | --- |
| `/servernavigator cancel` | keine zusätzliche; kein Wechsel-Cooldown |
| `/servernavigator status [server]` | `servernavigator.admin` |
| `/servernavigator reload` | `servernavigator.admin` |

Hauptbefehle/Aliase sind konfigurierbar und teilen pro Ziel denselben Handler. Ein Spieler hat höchstens einen Wunsch. Nach Ablauf des gemeinsamen 15-Sekunden-Cooldowns ersetzt ein anderes Ziel den bisherigen Wunsch. Abgewiesene Wiederholungen verlängern keine Frist und setzen keine Anzeige zurück. Derselbe bereits vorgemerkte Wunsch wird nicht erneut angelegt.

Mit `settings.require-server-permissions = true` gilt standardmäßig `server.<server-id>`. Pro Server überschreiben `require-permission` und `permission` diese Vorgabe. Normale Velocity-Permission-Abfragen unterstützen etwa LuckPerms auf dem Proxy. `servernavigator.admin` umgeht Ziel-Permissions nicht.

`ServerPreConnectEvent` kontrolliert ursprüngliches und ausgewähltes Ziel, auch für `/server <name>` und andere Plugins. Der eingebaute Befehl wird nicht überschrieben und erhält keine Navigator-Warteliste. Seine eigene Permission gilt zusätzlich. Andere Proxy-Plugins müssen bereits verweigerte Verbindungen respektieren. Direkte Backend-Zugänge benötigen weiterhin korrektes Forwarding und Netzwerkzugriffsschutz.

## Bereinigung, Reload und API-Grenzen

Erfolg, Cancel, Disconnect, Timeout, unabhängiger erfolgreicher Wechsel und gültiger Reload entfernen Wunsch und eigene Anzeigen. Ohne Wartende wird der Task entfernt. Späte Futures können entwertete Wünsche nicht wiederaufnehmen oder Folgeanmeldungen auslösen. Ein vorhandenes, funktionierendes Backend bleibt bei Verbindungsfehlern Aufenthaltsort. Der initiale Proxy-Login benötigt einen erreichbaren Aufenthaltsserver; kein Limbo.

Velocity bietet öffentlich keine garantierte Rücknahme eines schon zugelassenen Backend-Logins. Vor `ServerPreConnectEvent` verhindert der Generationsguard entwertete Anfragen. Nach diesem Ereignis kann ein bereits abgesendeter Login noch gelingen. Ein weiterer Versuch wird nicht über einen noch laufenden internen Versuch desselben Spielers gelegt. Es wird kein heimlicher Rückwechsel ausgeführt.

`attempt-timeout-ms` beendet einen nicht antwortenden Plugin-Versuch. HTTP nutzt zusätzlich echte Connect-/Request-Timeouts. Für Velocity-Anmeldungen außerdem `advanced.connection-timeout` und `advanced.read-timeout` in `velocity.toml` passend setzen; es gibt öffentlich keinen Socket-Timeout je einzelner Anfrage. Langsame Modded-Logins berücksichtigen.

Reload validiert erst Typen, Werte, Texte, Ziele und Befehlskonflikte; erst dann ersetzt er Snapshot und eigene Befehle. Fehlerhafte Registrierung stellt alte Befehle wieder her. Fremde Befehle werden nicht absichtlich entfernt. Andere Plugins dürfen währenddessen nicht dieselben Namen übernehmen. Ungültiger Reload erhält aktive Konfiguration und Wünsche.

| Änderung | Erforderlich |
| --- | --- |
| Texte, Modi, Zeiten, Permissions, Befehle/Aliase vorhandener Ziele | Plugin-Konfigurationsreload |
| Neues Backend, registrierter Zielname, IP oder Port | zuerst `velocity.toml` ändern und Proxy neu starten, danach Plugin-Konfiguration |
| Neue Plugin-JAR oder Proxy-Socket-Timeouts | Proxy-Neustart |
| Externer Startdienst | Dienst unabhängig einrichten; optionalen Trigger konfigurieren |

## Build und Prüfungen

Voraussetzungen: **Temurin JDK 25**, **Apache Maven 3.9.11**. `mvn -B -ntp clean verify` erzeugt `target/ServerNavigator-1.1.0.jar` und die Sources-JAR. TOML/ANTLR werden isoliert mitgeliefert; Velocity und Adventure stellt der Proxy bereit. Alle Abhängigkeiten und Maven-Plugins sind in `pom.xml` versioniert.

Das Projekt enthält Konfigurationsvalidierung/-migration, synchronisierte Zustandsmaschine, austauschbare Provider, reine Darstellungsklassen sowie den Velocity-Adapter. Tests liegen unter `src/test/java/`; [VALIDIERUNG.md](docs/VALIDIERUNG.md) trennt ausgeführte Prüfungen von notwendigen Live-Tests.

GitHub Actions baut und testet vor der Veröffentlichung. Releases enthalten JAR, vollständiges Quellen-ZIP, Sources-JAR, Konfiguration, Testberichte, Builddaten und SHA-256-Prüfsummen. Vorhandene Releases werden nicht überschrieben.

Offizielle Grundlagen: [Velocity API](https://docs.papermc.io/velocity/dev/creating-your-first-plugin/), [Adventure MiniMessage einschließlich Newline](https://docs.papermc.io/adventure/minimessage/format/), [Adventure Title](https://jd.advntr.dev/api/latest/net/kyori/adventure/title/Title.html).
