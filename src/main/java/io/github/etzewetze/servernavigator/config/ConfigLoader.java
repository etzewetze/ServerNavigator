package io.github.etzewetze.servernavigator.config;

import static io.github.etzewetze.servernavigator.config.NavigatorConfig.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import io.github.etzewetze.servernavigator.presentation.ComponentLines;
import io.github.etzewetze.servernavigator.presentation.TemplateText;
import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.velocity.MessageRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Strict types/unknown-key checks prevent an apparently successful, ineffective reload. */
public final class ConfigLoader {
    public static final Set<String> MESSAGE_KEYS = Set.of("no-permission", "player-only", "unknown-server",
        "already-there", "no-current-server", "cooldown", "cooldown-pending", "already-pending",
        "cancelled", "nothing-pending", "replaced", "timeout", "attempt-timeout", "attempt-limit", "rejected",
        "network-error", "start-error", "connection-cancelled", "connection-busy", "connected", "independent-switch",
        "reload-cancelled", "reloaded", "reload-failed", "usage", "status-idle", "status-active", "shutting-down");
    private static final Set<String> DISPLAY_KEYS = Set.of("modes", "update-interval-ms", "chat-interval-seconds", "bossbar-color",
        "bossbar-overlay", "title-fade-in-ms", "title-stay-ms", "title-fade-out-ms", "bossbar", "title", "subtitle", "actionbar",
        "chat", "bossbar-longer", "title-longer", "subtitle-longer", "actionbar-longer", "chat-longer", "wait-chat-enabled", "wait-chat",
        "status-checking", "status-starting", "status-longer", "status-connecting");
    private static final TagResolver TAGS = TagResolver.resolver(List.of("server", "servername", "seconds", "zeit", "elapsed", "cooldown", "status",
        "reason", "count", "waiters", "attempts").stream().map(s -> Placeholder.unparsed(s, "test")).toArray(TagResolver[]::new));
    private static final MiniMessage MINI = MiniMessage.builder().strict(true).build();

    public NavigatorConfig load(Path path, Predicate<String> registeredServer) throws IOException {
        return loadCandidate(path, registeredServer).config();
    }
    /** No file writes until the caller has also validated and registered proxy commands. */
    public record Candidate(NavigatorConfig config, String migratedText, String originalText) {
        public boolean migrated() { return migratedText != null; }
        public Path saveMigration(Path path) throws IOException {
            if (!migrated()) return null;
            if (!Files.readString(path).equals(originalText)) throw new IOException("Die Datei wurde inzwischen extern verändert; nicht überschrieben");
            Path parent = path.toAbsolutePath().getParent();
            Path backup = Files.createTempFile(parent, "config-v1-", ".backup.toml");
            Files.writeString(backup, originalText);
            Path temporary = Files.createTempFile(parent, "config-v2-", ".tmp");
            try {
                Files.writeString(temporary, migratedText);
                try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            return backup;
        }
    }
    public Candidate loadCandidate(Path path, Predicate<String> registeredServer) throws IOException {
        String original = Files.readString(path);
        var root = Toml.parse(original);
        if (root.hasErrors()) throw new IllegalArgumentException(root.errors().toString());
        long version = number(root, "config-version", 1, 1, 2);
        String migrated = version == 1 ? ConfigMigration.upgrade(root) : null;
        if (migrated != null) root = Toml.parse(migrated);
        if (root.hasErrors()) throw new IllegalArgumentException(root.errors().toString());
        keys(root, Set.of("config-version", "settings", "display", "messages", "servers"), "root");
        number(root, "config-version", 2, 2, 2);
        var settings = table(root, "settings", true);
        keys(settings, Set.of("require-server-permissions", "cooldown-seconds", "poll-interval-ms", "max-wait-seconds",
            "attempt-timeout-ms", "max-connection-attempts", "network-retries", "expected-startup-seconds", "starting-patterns", "terminal-patterns"), "settings");
        boolean require = bool(settings, "require-server-permissions", false);
        long cooldown = number(settings, "cooldown-seconds", 15, 0, 86400) * 1000;
        long poll = number(settings, "poll-interval-ms", 2000, 250, 60000);
        long wait = number(settings, "max-wait-seconds", 180, 1, 86400) * 1000;
        long timeout = number(settings, "attempt-timeout-ms", 10000, 250, 120000);
        int attempts = (int) number(settings, "max-connection-attempts", 100, 1, 10000);
        int networkRetries = (int) number(settings, "network-retries", 100, 0, 10000);
        long expectedStartup = number(settings, "expected-startup-seconds", 30, 1, 86400);
        List<Pattern> starting = patterns(settings, "starting-patterns");
        List<Pattern> terminal = patterns(settings, "terminal-patterns");
        var globalDisplay = display(table(root, "display", true), null);
        var messages = table(root, "messages", true);
        keys(messages, MESSAGE_KEYS, "messages");
        Map<String, String> texts = new HashMap<>();
        for (String key : MESSAGE_KEYS) texts.put(key, text(messages, key, null));
        var servers = table(root, "servers", true);
        if (servers.isEmpty()) throw new IllegalArgumentException("Mindestens ein Server ist erforderlich");
        Map<String, Backend> result = new LinkedHashMap<>();
        Set<String> targets = new HashSet<>(); Map<String, String> commands = new HashMap<>();
        commands.put("servernavigator", "Administration");
        for (String id : servers.keySet()) {
            identifier(id, "Server-ID"); var s = table(servers, id, true);
            keys(s, Set.of("velocity-name", "display-name", "command", "aliases", "require-permission", "permission",
                "expected-startup-seconds", "wake", "display"), "servers." + id);
            String name = string(s, "velocity-name", id);
            if (!registeredServer.test(name)) throw new IllegalArgumentException("Nicht in Velocity registriert: " + name);
            if (!targets.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Doppeltes Velocity-Ziel: " + name);
            String cmd = string(s, "command", id); identifier(cmd, "Befehl");
            List<String> aliases = strings(s, "aliases", List.of());
            var names = new ArrayList<>(aliases); names.add(cmd);
            for (String alias : names) {
                identifier(alias, "Alias"); String old = commands.putIfAbsent(alias.toLowerCase(Locale.ROOT), id);
                if (old != null) throw new IllegalArgumentException("Doppelter Befehl/Alias '" + alias + "': " + old + ", " + id);
            }
            String node = string(s, "permission", "server." + id);
            if (!node.matches("[a-zA-Z0-9_.:-]+")) throw new IllegalArgumentException("Ungültiger Permission-Node: " + node);
            Wake wake = wake(table(s, "wake", false));
            String displayName = text(s, "display-name", id); lines(displayName, 1, "display-name");
            result.put(id, new Backend(id, name, displayName, cmd, aliases,
                bool(s, "require-permission", require), node,
                number(s, "expected-startup-seconds", expectedStartup, 1, 86400) * 1000, wake,
                display(table(s, "display", false), globalDisplay)));
        }
        // Validate the composition too: a multiline status inserted into an otherwise short
        // title must not silently create a third Vanilla row.
        var renderer = new MessageRenderer();
        for (Backend backend : result.values()) for (Progress.Phase phase : Progress.Phase.values()) {
            var progress = new Progress(backend, 1, 1, 1, phase, .5f, 1, "Spieler", 1, Component.empty());
            Display d = backend.display(); boolean late = phase == Progress.Phase.LONGER;
            lines(renderer.render(late ? d.titleLonger() : d.title(), progress), 2, "title (" + backend.id() + ")");
            lines(renderer.render(late ? d.subtitleLonger() : d.subtitle(), progress), 1, "subtitle (" + backend.id() + ")");
        }
        return new Candidate(new NavigatorConfig(cooldown, poll, wait, timeout, attempts, networkRetries, result, texts, starting, terminal), migrated, original);
    }
    private Wake wake(TomlTable t) {
        if (t == null) return new Wake(WakeType.CONNECTION, null, "", "{}");
        keys(t, Set.of("type", "url", "token-environment", "body"), "wake");
        WakeType kind = value(WakeType.class, string(t, "type", "CONNECTION"));
        String url = string(t, "url", ""); String token = string(t, "token-environment", "");
        String body = string(t, "body", "{}");
        if (kind == WakeType.CONNECTION) {
            if (!url.isEmpty() || !token.isEmpty()) throw new IllegalArgumentException("CONNECTION benötigt keine HTTP-Einstellungen");
            return new Wake(kind, null, "", body);
        }
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
            || url.contains("EXAMPLE") || uri.getHost().endsWith(".invalid"))
            throw new IllegalArgumentException("HTTP-Starttrigger benötigt eine echte HTTPS-URL ohne Zugangsdaten");
        if (!token.isEmpty() && (!token.matches("[A-Z_][A-Z_0-9]*") || System.getenv(token) == null || System.getenv(token).isBlank()))
            throw new IllegalArgumentException("Token-Umgebungsvariable fehlt oder ist ungültig: " + token);
        return new Wake(kind, uri, token, body);
    }
    private Display display(TomlTable t, Display base) {
        if (t == null) return base;
        keys(t, DISPLAY_KEYS, "display");
        Set<Mode> modes = new HashSet<>();
        for (String name : strings(t, "modes", base == null ? List.of("TITLE") : base.modes().stream().map(Enum::name).toList())) {
            if (!modes.add(value(Mode.class, name))) throw new IllegalArgumentException("Doppelter Anzeigemodus: " + name);
        }
        if (modes.isEmpty() || modes.contains(Mode.NONE) && modes.size() != 1) throw new IllegalArgumentException("NONE nur allein verwenden");
        Display display = new Display(modes,
            number(t, "update-interval-ms", base == null ? 500 : base.updateInterval(), 100, 60000),
            number(t, "chat-interval-seconds", base == null ? 10 : base.chatInterval() / 1000, 1, 3600) * 1000,
            value(BossBar.Color.class, string(t, "bossbar-color", base == null ? "PURPLE" : base.color().name())),
            value(BossBar.Overlay.class, string(t, "bossbar-overlay", base == null ? "PROGRESS" : base.overlay().name())),
            number(t, "title-fade-in-ms", base == null ? 0 : base.fadeIn(), 0, 60000),
            number(t, "title-stay-ms", base == null ? 1100 : base.stay(), 0, 60000),
            number(t, "title-fade-out-ms", base == null ? 200 : base.fadeOut(), 0, 60000),
            text(t, "bossbar", base == null ? "<server> startet <gray>| ~<seconds> s</gray>" : base.bossbar()),
            text(t, "title", base == null ? "<server> <gold>Startet</gold>" : base.title()),
            text(t, "subtitle", base == null ? "<gray>~<seconds> Sekunden</gray>" : base.subtitle()),
            text(t, "actionbar", base == null ? "<server> startet <gray>| ~<seconds> s</gray>" : base.actionbar()),
            text(t, "chat", base == null ? "<server> | <status> | voraussichtlich noch <seconds> s" : base.chat()),
            text(t, "bossbar-longer", base == null ? "<server> | <status>" : base.bossbarLonger()),
            text(t, "title-longer", base == null ? "<server> <gold>Startet</gold>" : base.titleLonger()),
            text(t, "subtitle-longer", base == null ? "<yellow>Start dauert länger …</yellow>" : base.subtitleLonger()),
            text(t, "actionbar-longer", base == null ? "<server> | <status>" : base.actionbarLonger()),
            text(t, "chat-longer", base == null ? "<server> | <status>" : base.chatLonger()),
            bool(t, "wait-chat-enabled", base == null || base.waitChatEnabled()),
            text(t, "wait-chat", base == null ? "<gray>Sobald der Server antwortet, wirst du teleportiert.</gray>" : base.waitChat()),
            text(t, "status-checking", base == null ? "Verbindung wird geprüft …" : base.checking()),
            text(t, "status-starting", base == null ? "startet" : base.starting()),
            text(t, "status-longer", base == null ? "Start dauert länger …" : base.longer()),
            text(t, "status-connecting", base == null ? "Verbindung wird hergestellt …" : base.connecting()));
        lines(display.title(), 2, "title"); lines(display.titleLonger(), 2, "title-longer");
        lines(display.subtitle(), 1, "subtitle"); lines(display.subtitleLonger(), 1, "subtitle-longer");
        return display;
    }
    private static void lines(String template, int maximum, String key) {
        lines(MINI.deserialize(template, TAGS), maximum, key);
    }
    private static void lines(Component component, int maximum, String key) {
        if (ComponentLines.split(component).size() > maximum)
            throw new IllegalArgumentException(key + " unterstützt höchstens " + maximum + " Zeile(n). Vanilla hat nur Titel und Untertitel.");
    }
    private List<Pattern> patterns(TomlTable t, String key) {
        var out = new ArrayList<Pattern>();
        for (String p : strings(t, key, null)) {
            if (p.isBlank() || p.length() > 512) throw new IllegalArgumentException("Ungültiges Muster: " + key);
            out.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        if (out.isEmpty()) throw new IllegalArgumentException(key + " darf nicht leer sein");
        return out;
    }
    private static void identifier(String value, String kind) {
        if (!value.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException(kind + " muss kleingeschrieben sein, ohne /: " + value);
    }
    private static void keys(TomlTable t, Set<String> allowed, String at) {
        for (String key : t.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unbekannte Einstellung: " + at + "." + key);
    }
    private static TomlTable table(TomlTable t, String key, boolean required) {
        Object v = t.get(key);
        if (v == null && !required) return null;
        if (!(v instanceof TomlTable tt)) throw new IllegalArgumentException("Tabelle fehlt/ungültig: " + key);
        return tt;
    }
    private static String string(TomlTable t, String key, String fallback) {
        Object v = t.get(key);
        if (v == null && fallback != null) return fallback;
        if (!(v instanceof String s)) throw new IllegalArgumentException("Text fehlt/ungültig: " + key);
        return s;
    }
    private static String text(TomlTable t, String key, String fallback) {
        String text = TemplateText.normalize(string(t, key, fallback));
        try { MINI.deserialize(text, TAGS); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Ungültiger MiniMessage-Text bei " + key + ": " + error.getMessage(), error); }
        return text;
    }
    private static boolean bool(TomlTable t, String key, boolean fallback) {
        Object v = t.get(key);
        if (v == null) return fallback;
        if (!(v instanceof Boolean b)) throw new IllegalArgumentException("Boolean erwartet: " + key);
        return b;
    }
    private static long number(TomlTable t, String key, long fallback, long min, long max) {
        Object v = t.get(key); long n = fallback;
        if (v != null) { if (!(v instanceof Long l)) throw new IllegalArgumentException("Ganzzahl erwartet: " + key); n = l; }
        if (n < min || n > max) throw new IllegalArgumentException(key + " muss zwischen " + min + " und " + max + " liegen");
        return n;
    }
    private static List<String> strings(TomlTable t, String key, List<String> fallback) {
        Object v = t.get(key);
        if (v == null && fallback != null) return fallback;
        if (!(v instanceof TomlArray a)) throw new IllegalArgumentException("Textliste fehlt/ungültig: " + key);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) { if (!(a.get(i) instanceof String s)) throw new IllegalArgumentException("Textliste erwartet: " + key); out.add(s); }
        return out;
    }
    private static <E extends Enum<E>> E value(Class<E> type, String s) {
        try { return Enum.valueOf(type, s); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Ungültiger Wert für " + type.getSimpleName() + ": " + s); }
    }
}
