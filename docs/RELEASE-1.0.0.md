# ServerNavigator 1.0.0

Vollständiges Velocity-Plugin für AMP-Sleep und automatische Serverwechsel. Ziel: Velocity 4.3.2 Build 48 / Java 25; offizielle API 4.3.0.

- Fünf vorkonfigurierte, beliebig erweiterbare Ziele mit konfigurierbaren Befehlen/Aliasen.
- Gemeinsamer AMP-Start je Ziel und automatische echte Login-Prüfung ohne Ping-Heuristik.
- Gemeinsamer 15-Sekunden-Cooldown, optionale Permissions einschließlich Schutz von `/server`.
- Adventure-Bossbar, Title, Actionbar und Chat, MiniMessage, getrennte Schätzung und maximale Wartefrist.
- Cancel, validierter Reload mit Befehls-Rollback, Status, begrenzte Wiederholungen und Bereinigung.
- Implementierte AMP-Login-/HTTPS-POST-Provider und austauschbare ReadinessChecker-Schnittstelle.
- Automatisierte Tests für konkurrierende Wünsche, Permissions, späte Antworten und Konfiguration.

**Installation:** Nur `ServerNavigator-1.0.0.jar` auf dem Proxy installieren. Alle fünf Zielnamen müssen in Velocity registriert sein. Die deutsche README und kommentierte `config.toml` erklären AMP, Permissions und Erweiterungen.

**Grenzen:** Kein Limbo und keine frei skalierbare Vanilla-Schriftgröße. Ein bereits über Velocitys Pre-Connect-Ereignis hinaus laufender Login kann mit der öffentlichen API nicht garantiert zurückgenommen werden. AMP-Meldungsmuster sind versions-/sprachabhängig. Live-AMP-/Minecraft-Tests und Client-Anzeigeprüfung sind zusätzlich erforderlich; siehe `docs/VALIDIERUNG.md`.
