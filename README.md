# ServerNavigator

Velocity-Plugin für gemeinsame Serverstarts mit **CubeCoders AMP**, automatischem Serverwechsel und Adventure-Anzeigen. Die Plugin-JAR gehört ausschließlich auf den Proxy. Ein Spieler wartet auf seinem bisherigen Backend; ein Limbo-Server wird nicht bereitgestellt.

Zielumgebung: **Velocity 4.3.2 Build 48, Temurin/Java 25** auf dem Proxy. Das Projekt verwendet die offizielle **Velocity API 4.3.0**, die dokumentierte stabile API dieser Versionslinie. Java 21–25 auf Backends ist für ein reines Proxy-Plugin unerheblich; der Proxy benötigt hier Java 25. Keine Velocity-Interna oder erfundene Start-API.

## Installation

1. Aus [Releases](https://github.com/etzewetze/ServerNavigator/releases) `ServerNavigator-1.0.0.jar` herunterladen. Nicht das `-sources.jar` installieren.
2. Proxy stoppen, die JAR in dessen `plugins/` legen. Auf Backends wird keine JAR benötigt.
3. Alle fünf Server unter `[servers]` in `velocity.toml` registrieren. Beispiel mit **anzupassenden** Adressen:

   ```toml
   [servers]
   lobby = "127.0.0.1:25566"
   challenges = "127.0.0.1:25567"
   survival = "127.0.0.1:25568"
   creative = "127.0.0.1:25569"
   testserver = "127.0.0.1:25570"
   try = ["lobby"]
   ```

4. Proxy starten. `plugins/servernavigator/config.toml` wird angelegt. Unbekannte Ziele oder fremde Befehlskonflikte verhindern die Aktivierung mit verständlichem Logeintrag; fremde Befehle bleiben erhalten.
5. AMP wie unten einrichten. Auf einem erreichbaren Aufenthaltsserver einloggen und `/survival` testen.

Die mitgelieferte [config.toml](src/main/resources/config.toml) ist vollständig kommentiert. Sie kann vor dem ersten Start direkt nach `plugins/servernavigator/config.toml` kopiert werden. ServerNavigator registriert keine Backend-Adressen selbst.

## Befehle

| Ziel-ID | Hauptbefehl | Aliase |
| --- | --- | --- |
| lobby | `/lobby` | `/l`, `/hub` |
| challenges | `/challenges` | `/challenge`, `/ch` |
| survival | `/survival` | `/surv` |
| creative | `/creative` | `/cre` |
| testserver | `/testserver` | `/test` |

Alle Einträge sind konfigurierbar. Hauptbefehl und Aliase verwenden dieselbe Handler-Instanz und Ziel-Permission. Namen kleingeschrieben und ohne `/` angeben.

| Befehl | Berechtigung | Wirkung |
| --- | --- | --- |
| `/servernavigator cancel` | keine zusätzliche | Eigenen Wunsch und Anzeigen entfernen; kein Wechsel-Cooldown |
| `/servernavigator status [server]` | `servernavigator.admin` | Aktive Vorgänge, vergangene Zeit und Wartende |
| `/servernavigator reload` | `servernavigator.admin` | Vollständig validierte Konfiguration samt Befehlen übernehmen |

`status` ohne aktiven Wunsch sagt „kein aktiver Startvorgang“ statt „offline“. Es startet keinen Ping und hält keine Server wach. `cancel` bleibt auch bei Pflicht-Permissions zugänglich. Reload und Status funktionieren ebenfalls von der Konsole.

## AMP einrichten: Starttrigger und echte Bereitschaft

Dein AMP startet beim **Beitrittsversuch** und lehnt diesen zunächst mit einer Startmeldung ab. Dafür ist `wake.type = "AMP_LOGIN"` implementiert und für alle Ziele voreingestellt. Panel-Zugangsdaten werden nicht benötigt.

1. In jeder AMP-Minecraft-Instanz Sleep Mode aktivieren und den Wake-Modus auf Spielerbeitritt stellen, je nach AMP-Version etwa **When a user joins the server**. Nicht den Ping-Modus wählen. Die Velocity-Zieladresse muss zum AMP-Sleep-Listener führen, auch wenn das Backend schläft.
2. Deine bestehenden **fünf Minuten Leerlauf nach dem letzten Spieler** und **zwei Minuten nach ungenutztem Aufwecken** in AMP belassen. Das Plugin verändert diese Timer nicht.
3. Ein Testziel schlafen lassen und `/testserver` ausführen. Falls die Startmeldung als normale Ablehnung erscheint, ihren tatsächlichen Klartext mit `settings.starting-patterns` abgleichen und ein passendes enges Regex ergänzen. Deutsch/Englisch-Beispiele sind enthalten; die genaue Meldung hängt von AMP-Version und Sprache ab.

`AmpLoginWakeupProvider` sendet eine **normale asynchrone Velocity-Verbindungsanfrage des berechtigten Spielers**. Ist das Ziel bereit, gelingt der Wechsel sofort. Schläft es, übernimmt AMP den Start. Weitere Spieler hängen sich an denselben Vorgang und senden in dieser Phase keinen zusätzlichen Erst-Starttrigger.

`VelocityLoginReadinessChecker` prüft durch einen weiteren **echten Login des ersten berechtigten Wartenden**. Ein erfolgreicher Login ist gleichzeitig Bereitschaftsnachweis und Verbindung dieses Spielers. Anschließend werden die übrigen Wartenden sofort verbunden, jeweils mit erneuter Permission-Prüfung. Solange noch niemand aufgenommen wurde, läuft nur ein gemeinsamer Prüfversuch je Ziel.

**Weder Ping noch offener TCP-Port gelten als Bereitschaft.** AMP kann Anfragen selbst beantworten. Stattdessen werden Kapazität, Whitelist, Wartung, Forwarding und echte Spieleraufnahme im regulären Login ausgewertet. Es gibt keine Probe-Bots. Die Prüfversuche sind reale Logins: falls AMP während des Wartens wieder einschläft, können sie naturgemäß erneut dessen Wake-Logik erreichen. Der konfigurierte WakeupProvider wird trotzdem nur einmal pro gemeinsamem Vorgang aufgerufen.

Sobald alle Wünsche enden, wird der Scheduler-Task entfernt. Kein Heartbeat hält ungenutzte Server wach. Ein bereits von AMP begonnener Start wird durch `cancel` nicht heruntergefahren; ohne Beitritt greift dein AMP-Timer. Für den ersten Proxy-Beitritt muss ein Aufenthaltsserver verfügbar sein: das Plugin ersetzt keinen initialen Login-/Fallback-Dienst.

### Fehler und begrenzte Wiederholungen

- Nur eine zu `starting-patterns` passende Login-Ablehnung bestätigt einen AMP-Start.
- `terminal-patterns` haben Vorrang: voll, Whitelist, Wartung, Ban, Versions- und Authentifizierungsfehler gelten nicht als Schlaf. Unbekannte Ablehnungen sind terminal; der originale Backend-Grund wird als Adventure-Komponente angezeigt.
- Unbekannte Netzwerkfehler gelten nicht als Schlaf. Erst **nach bestätigter AMP-Startantwort** werden wenige aufeinanderfolgende Netzwerkfehler toleriert, etwa während AMP den Port an Java übergibt. Standard: drei. Eine weitere Startantwort setzt diesen Fehlerzähler zurück.
- Zusätzlich begrenzen 100 Versuche pro Wunsch und 180 Sekunden Gesamtwartezeit den Ablauf. Der Erstversuch zählt mit.
- Bei Fehlern bleibt der Spieler auf seinem weiterhin funktionierenden bisherigen Backend. Wird dieses unabhängig beendet, kann das Plugin dessen Verbindung nicht erhalten.

### Timeouts und Abbruchgrenze der öffentlichen API

`attempt-timeout-ms` beendet einen nicht antwortenden Plugin-Versuch samt Wunsch; HTTP verwendet außerdem echte Connect-/Request-Timeouts. Velocity bietet über `ConnectionRequestBuilder` keinen je Anfrage einstellbaren Socket-Timeout und keine garantierte Rücknahme eines bereits laufenden Backend-Logins. Deshalb zusätzlich in **velocity.toml**, `[advanced]`, passende `connection-timeout`- und `read-timeout`-Werte in Millisekunden einstellen, beispielsweise 5000 und 10000. Die Plugin-Obergrenze mindestens passend zum erwarteten Login-Timeout wählen; langsame Modded-Logins berücksichtigen.

Abbruch/Ersetzung/Disconnect/Reload entwerten Wünsche sofort. Verspätete Futures lösen keine Folgeversuche oder Wiederaufnahme aus. Ein Guard in `ServerPreConnectEvent` verweigert entwertete Anfragen, bevor sie dieses Ereignis passieren. **Einen bereits darüber hinaus fortgeschrittenen Login kann die öffentliche API nicht garantiert stoppen.** In diesem engen Rennen kann ein schon abgesendeter Wechsel noch gelingen. Weitere Versuche werden nicht über den noch laufenden internen Versuch gelegt. Es erfolgt kein heimlicher zusätzlicher Rückwechsel. Diese Grenze wird ausdrücklich nicht als Rücknahmegarantie verkauft.

## Permissions und `/server <name>`

Standardmäßig sind Server-Permissions optional (`require-server-permissions = false`). Mit `true` ist grundsätzlich `server.<server-id>` erforderlich. Pro Server überschreiben `require-permission` und `permission` die Vorgabe. Ein eigener Node gilt für Hauptbefehl und sämtliche Aliase.

Beispiel eines beschränkten Ziels:

```toml
[servers.testserver]
velocity-name = "testserver"
display-name = "Testserver"
command = "testserver"
aliases = ["test"]
expected-startup-seconds = 30
require-permission = true
permission = "server.testserver"
```

LuckPerms auf dem Proxy nutzen und beispielsweise dessen Konsolenbefehle verwenden:

```text
lpv group default permission set server.lobby true
lpv group member permission set server.survival true
lpv group admin permission set servernavigator.admin true
```

Gruppennamen an dein Netzwerk anpassen. Das Plugin nutzt normale `Player.hasPermission()`-/`CommandSource.hasPermission()`-Abfragen. Es enthält keinen eigenen Permission-Provider und keinen impliziten Admin-Bypass für Ziel-Permissions.

`ServerPreConnectEvent` wird mit `PostOrder.LAST` geprüft: ursprüngliches **und aktuell ausgewähltes** Ziel werden kontrolliert. Das schützt auch `/server <name>`, andere Plugins und die initiale Zielauswahl. Ohne Permission darf kein Login den Sleep-Listener erreichen. Die eigene Navigation prüft zusätzlich vor dem Starttrigger, vor jeder automatischen Verbindung und während des Wartens auf Permission-Entzug.

Der eingebaute `/server`-Befehl wird **nicht überschrieben**; seine eigene Permission gilt zusätzlich. Er bekommt durch diese Zugriffskontrolle weder unsere Warteliste noch unseren Wechsel-Cooldown. Ein unabhängiger erfolgreicher Wechsel beendet einen alten Navigator-Wunsch. Direkte Backend-Zugänge benötigen weiterhin korrektes Forwarding und Netzwerkzugriffsschutz. Andere Proxy-Plugins gelten als vertrauenswürdig und dürfen eine verweigerte Verbindung nicht nachträglich freigeben.

## Cooldown, Warteliste und Anzeigen

Der Standard-Cooldown beträgt **15 Sekunden pro Spieler gemeinsam für alle Navigator-Ziele und Aliase**. Nur ein neu angenommener Wunsch setzt ihn. Abgewiesene Wiederholungen verändern weder Frist, Vorgang noch Anzeige. Nach Ablauf ersetzt ein anderes Ziel den alten Wunsch; dasselbe Ziel bestätigt ihn ohne Neustart. Die automatische Verbindung wartet nie auf den Cooldown. Cancel setzt einen laufenden Cooldown nicht zurück.

Pro Ziel gibt es einen gemeinsamen Vorgang, pro Spieler höchstens einen Wunsch. Disconnect, Erfolg, Abbruch, unabhängiger Wechsel, gültiger Reload und Timeout entfernen Wunsch und eigene Anzeigen. Auch Tasks werden entfernt, sobald sie nicht mehr benötigt werden.

`BOSSBAR`, `TITLE`, `ACTIONBAR`, `CHAT` können unabhängig kombiniert werden; `NONE` steht allein. Teilüberschreibungen unter `[servers.<id>.display]` erben alle übrigen globalen Werte. Bossbar-Farbe und Unterteilung verwenden die tatsächlich verfügbaren Adventure-Werte. Der Balken zeigt den **verbleibenden Anteil der geschätzten Startzeit**, keinen gemessenen Boot-Fortschritt.

`expected-startup-seconds` gilt pro Server und ist ausdrücklich eine Schätzung. Hinzukommende sehen den verbleibenden Teil derselben Schätzung. Die maximale Wartefrist läuft für jeden Spieler ab seinem eigenen Wunsch. Nach Schätzungsende bleiben Sekunden bei null; separate `*-longer`-Vorlagen zeigen „Start dauert länger …“. Eine abgelaufene Schätzung gibt keinen Server frei.

MiniMessage unterstützt Hexfarben und Verläufe; Tags müssen geschlossen sein. Platzhalter: `<server>`, `<seconds>`, `<elapsed>`, `<cooldown>`, `<status>`, zusätzlich `<reason>`, `<count>`, `<waiters>`, `<attempts>`. In `status-*` ist `<status>` selbst leer. Fremde Texte werden als Komponenten oder unformatierte Werte eingefügt. Vanilla-Title besitzt eine feste Schriftgröße. Die Standardtexte sind ohne Resourcepack nutzbar; spätere Pack-Glyphen können über `<font:meinpack:anzeigen>...</font>` eingebaut werden. Die gelieferte normale Textkonfiguration ist der Fallback.

## Weitere Server und Reload

Einen neuen `[servers.<id>]`-Block kopieren und eindeutige Befehle/Aliase vergeben. Codeänderungen sind nicht nötig.

| Änderung | Plugin-Reload | Zusätzlich |
| --- | --- | --- |
| Texte, Farben, Modi, Schätzung, Permissions, Intervalle, Cooldown | ja | nichts |
| Befehle/Aliase vorhandener Ziele | ja | Fremd-Konflikte auflösen |
| Plugin-ID/Anzeigename bei gleichem Velocity-Ziel | ja | Standard-Permission bei ID-Wechsel beachten |
| Neues Backend, registrierter Name, IP oder Port | danach ja | zuerst `[servers]` in `velocity.toml` ändern, Velocity neu starten |
| Sleep-Timer oder Wake-Modus | ggf. Regex-Reload | in AMP ändern |
| Proxy-Socket-Timeouts/Forwarding | ggf. Plugin-Frist ändern | Velocity-Konfiguration ändern, Proxy neu starten |

Reload validiert zuerst Typen, Bereiche, Texte, Regex, eindeutige Aliase, registrierte Ziele und fremde Proxy-Befehle. Erst danach werden eigene Befehle neu registriert und der unveränderliche Snapshot ersetzt. Registrierungsfehler stellen alte eigene Befehle wieder her. Ein gültiger Reload beendet bestehende Wünsche mit Nachricht. Bei ungültiger Datei bleiben Snapshot und Wünsche aktiv. Die öffentliche Command-API bietet keine Transaktion über mehrere Befehle; synchrones Neuregistrieren mit Rollback setzt voraus, dass andere Plugins nicht gleichzeitig dieselben Namen übernehmen.

## Optionaler HTTP-Startdienst und eigene Provider

Für dein AMP-Sleep ist der Login-Provider fertig implementiert. Alternativ sendet `wake.type = "HTTP"` einen konfigurierten **HTTPS-POST** mit wörtlichem JSON-Body und optionalem Bearer-Token aus einer Umgebungsvariable. Pro Ziel eine vollständige URL angeben. Nur 2xx bestätigt den Trigger, keine Redirects, keine POST-Wiederholung. Endpunkt und Token werden nicht geloggt. Die Bereitschaft prüft weiterhin der echte Login.

`https://AMP-BRIDGE-EXAMPLE.invalid/...` im Beispiel ist ein **absichtlich ungültiger Platzhalter**, kein angeblicher AMP-Endpunkt. Eine eigene Brücke muss zur installierten AMP-Version passende Anmeldung/API kapseln. Der generische POST behauptet nicht, eine AMP-Sitzungsverwaltung zu ersetzen; dein vorhandenes Sleep benötigt keinen HTTP-Dienst.

Entwickler können `WakeupProvider` und `ReadinessChecker` implementieren und am Engine-Konstruktor verdrahten. Methoden liefern sofort `CompletionStage<AttemptResult>`, ohne blockierende Arbeit. Der `AttemptContext` enthält den Autorisierungs-/Generationsguard; unmittelbar vor Nebenwirkungen prüfen. `CONNECTED` bedeutet tatsächlich verbunden. Die Standardprovider sind vollständig implementiert.

## Build und Tests

Voraussetzungen: **Temurin JDK 25**, **Apache Maven 3.9.11**. Im Projektverzeichnis:

```sh
java -version
mvn -version
mvn -B -ntp clean verify
```

Ergebnis: `target/ServerNavigator-1.0.0.jar`, installierbar inklusive TOML-Parser. Velocity und Adventure stellt der Proxy bereit; sie werden nicht mitkopiert. Maven Shade isoliert TOML/ANTLR-Pakete. Abhängigkeiten und Maven-Plugins sind konkret in `pom.xml` versioniert. Der Build benötigt Maven Central und das offizielle PaperMC-Repository.

```text
pom.xml
src/main/resources/{config.toml,velocity-plugin.json}
src/main/java/io/github/etzewetze/servernavigator/
  ServerNavigatorPlugin.java
  config/       # Validierung und unveränderlicher Snapshot
  core/         # Synchronisierte Zustandsmaschine, Fristen, Cooldown
  provider/     # AMP-Login, echter Login-Check, HTTPS-POST
  velocity/     # Adventure, Adapter und Befehlsregistrierung
src/test/java/io/github/etzewetze/servernavigator/
  NavigationEngineTest.java
  ConfigurationTest.java
  ClassifierAndMessagesTest.java
  CommandRegistryTest.java
docs/{RELEASE-1.0.0.md,VALIDIERUNG.md}
.github/workflows/build-release.yml
```

Die Tests prüfen Cooldown, parallele Anfragen und genau einen Wake-Aufruf, Permission-Entzug, direkte Ziel-Zugriffskontrolle, Abbruch/Ersetzung, verspätete Futures, Event-vor-Future, Timeouts, Schätzungen, begrenzte Fehlerwiederholungen, Konfigurationsfehler und Befehls-Rollback. Nur der Test-Harness wartet auf Test-Threads. Der Plugin-Befehlsablauf enthält weder `Thread.sleep()` noch blockierende Future-Abfragen.

Ausgeführte Prüfungen und notwendige Live-Tests: [VALIDIERUNG.md](docs/VALIDIERUNG.md). GitHub Actions baut mit Java 25, führt `verify` aus und veröffentlicht auf `main` nach Erfolg eine noch nicht vorhandene Versionsfreigabe mit JAR, Quellen, Konfiguration, Testbericht und SHA-256-Prüfsummen. Vorhandene Releases werden nicht überschrieben. Für Folgeversionen POM, Plugin-Metadaten und passende Release-Notizen gemeinsam aktualisieren.

## Offizielle Grundlagen

- [Velocity: Projekt und API](https://docs.papermc.io/velocity/dev/creating-your-first-plugin/)
- [Velocity: ConnectionRequestBuilder](https://jd.papermc.io/velocity/4.1.0/com/velocitypowered/api/proxy/ConnectionRequestBuilder.html)
- [Velocity: ServerPreConnectEvent](https://github.com/PaperMC/Velocity/blob/dev/3.0.0/api/src/main/java/com/velocitypowered/api/event/player/ServerPreConnectEvent.java)
- [Velocity: Scheduler](https://docs.papermc.io/velocity/dev/scheduler-api/)
- [Adventure: MiniMessage](https://docs.advntr.dev/minimessage/index.html)
- [CubeCoders: AMP-Sleep-Prinzip](https://www.patreon.com/cubecoders/posts/universal-sleep-56709601)
- [CubeCoders: Ping-/Join-Wake-Modus](https://github.com/CubeCoders/AMP/issues/795)
