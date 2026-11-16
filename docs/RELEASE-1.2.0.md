# ServerNavigator 1.2.0

- Öffentliche optionale Schnittstelle für ServerHub 2.4.0: Menü-Klicks verwenden dieselbe Warteliste, Zeiten, Starttrigger, Wiederholungen und Rechte wie eigene Befehle.
- Abfrage laufender Vorgänge und wartender Spieler zur koordinierten Status-/HUD-Anzeige.
- Kein neuer Konfigurationsschritt; das Format und vorhandene Einstellungen bleiben erhalten.
- Ein IDLE-Vorgang ist ausdrücklich kein Nachweis für einen laufenden oder schlafenden Server.

Neue JAR ausschließlich auf Velocity installieren und Proxy vollständig neu starten. Java 25, Velocity API 4.3.0. Automatisierte Prüfungen über GitHub Actions; kein vollständiger Live-Test im Netzwerk.
