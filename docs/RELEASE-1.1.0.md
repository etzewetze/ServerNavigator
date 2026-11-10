# ServerNavigator 1.1.0

Unabhängige Servernavigation für Velocity 4.3.x / Java 25 mit kompakteren Anzeigen.

- Keine Bindung an ein bestimmtes Hosting-Panel. Normale `CONNECTION`-Anfragen oder optionaler allgemeiner HTTPS-POST-Starttrigger.
- Auch nicht erreichbare Server können ohne spezielle Startmeldung vorgemerkt werden; begrenzte Folgeversuche verbinden automatisch nach erfolgreichem Login.
- Globale Standardwartezeit `settings.expected-startup-seconds = 30`; optionaler gleichnamiger Wert pro Server überschreibt sie. Der Timer bleibt eine Schätzung, kein künstlicher Verbindungsaufschub.
- Standardtitel: **Testserver Startet**, darunter **~23 Sekunden**. Alternativ nur Bossbar; Texte und Auswahl global oder je Server anpassbar.
- Einmaliger Chattext bei beiden Varianten: **Sobald der Server antwortet, wirst du teleportiert.** Separat konfigurierbar/deaktivierbar; kein wiederholter Chatmodus nötig.
- Newline über `<newline>`, `<br>`, `\n` oder echte Umbrüche. Titel werden auf die zwei Vanilla-Zeilen verteilt, Farben/Formatierungen bleiben erhalten. Mehrzeiliger Chat; Bossbar/Actionbar bleiben einzeilig.
- Zusätzliche Platzhalter-Aliase `<servername>` und `<zeit>`.
- Bestehende Konfiguration wird validiert, gesichert und auf Format 2 mit dem neuen Layout umgestellt. Server, Befehle, Permissions und individuelle Zeiten bleiben erhalten.

**Update:** Proxy stoppen, alte JAR durch `ServerNavigator-1.1.0.jar` ersetzen, Proxy neu starten. Keine zweite Plugin-Version parallel installieren. Die Kommentierung der neuen Beispielkonfiguration erläutert alle Optionen.

Permissions, gemeinsamer Cooldown, Abbruch, Status, validierter Reload und Bereinigung bleiben erhalten. Bereits von Velocity zugelassene Logins können über die öffentliche API nicht garantiert zurückgenommen werden. Echte Spielerwechsel und Client-Darstellung zusätzlich im eigenen Netzwerk prüfen; siehe `docs/VALIDIERUNG.md`.
