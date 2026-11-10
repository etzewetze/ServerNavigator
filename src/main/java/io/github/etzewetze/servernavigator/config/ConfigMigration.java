package io.github.etzewetze.servernavigator.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Pure schema-1 to schema-2 conversion. The caller validates before saving a backup/replacement. */
final class ConfigMigration {
    private static final Set<String> OLD_LAYOUT = Set.of("title", "subtitle", "bossbar", "actionbar", "chat",
        "title-longer", "bossbar-longer", "actionbar-longer", "chat-longer");
    private ConfigMigration() { }
    static String upgrade(TomlTable root) throws IOException {
        Map<String, Object> data = copy(root); data.put("config-version", 2L);
        Map<String, Object> settings = child(data, "settings");
        Object oldRetries = settings.remove("transient-retries");
        settings.putIfAbsent("network-retries", oldRetries instanceof Long n && n != 3 ? n : 100L);
        settings.putIfAbsent("expected-startup-seconds", 30L);
        child(data, "messages").remove("queued");
        Map<String, Object> globalDisplay = child(data, "display"); compact(globalDisplay);
        // Materialize the new layout in the migrated file so users can edit it directly,
        // rather than having to discover invisible fallback values in the implementation.
        try (var resource = ConfigMigration.class.getResourceAsStream("/config.toml")) {
            if (resource == null) throw new IOException("Kommentierte Standardkonfiguration fehlt");
            var defaults = Toml.parse(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            if (defaults.hasErrors() || !(defaults.get("display") instanceof TomlTable display))
                throw new IOException("Eingebettete Anzeige-Standardeinstellungen sind ungültig");
            var layout = copy(display);
            for (String key : OLD_LAYOUT) globalDisplay.put(key, layout.get(key));
            for (String key : List.of("subtitle-longer", "wait-chat-enabled", "wait-chat", "modes")) globalDisplay.putIfAbsent(key, layout.get(key));
        }
        for (Object value : child(data, "servers").values()) {
            if (!(value instanceof Map<?, ?>)) continue; // normal validation reports malformed entries
            @SuppressWarnings("unchecked") Map<String, Object> server = (Map<String, Object>) value;
            if (server.get("wake") instanceof Map<?, ?>) {
                var wake = child(server, "wake");
                // Schema 1 offered one connection-based default and the generic HTTP provider.
                if (!"HTTP".equals(wake.get("type"))) wake.put("type", "CONNECTION");
            }
            if (server.get("display") instanceof Map<?, ?>) compact(child(server, "display"));
        }
        StringBuilder out = new StringBuilder("# ServerNavigator 1.1.0 – automatisch auf Konfigurationsformat 2 aktualisiert.\n"
            + "# Die ursprüngliche Datei liegt als config-v1-*.backup.toml daneben.\n"
            + "# Kompaktes Anzeige-Layout; Server, Berechtigungen, Zeiten und eigene Befehle bleiben erhalten.\n"
            + "# Vollständig kommentiertes Beispiel: https://github.com/etzewetze/ServerNavigator/blob/main/src/main/resources/config.toml\n");
        writeTable(out, data, ""); return out.toString();
    }
    private static void compact(Map<String, Object> display) {
        OLD_LAYOUT.forEach(display::remove);
        if (display.get("modes") instanceof List<?> modes && modes.size() == 3
            && modes.containsAll(List.of("TITLE", "BOSSBAR", "ACTIONBAR"))) display.put("modes", List.of("TITLE"));
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> child(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
        if (value != null) throw new IllegalArgumentException("Tabelle erwartet: " + key);
        var child = new LinkedHashMap<String, Object>(); map.put(key, child); return child;
    }
    private static Map<String, Object> copy(TomlTable table) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : table.keySet()) result.put(key, copyValue(table.get(key)));
        return result;
    }
    private static Object copyValue(Object value) {
        if (value instanceof TomlTable table) return copy(table);
        if (value instanceof TomlArray array) {
            var list = new ArrayList<>(); for (int i = 0; i < array.size(); i++) list.add(copyValue(array.get(i))); return list;
        }
        return value;
    }
    private static void writeTable(StringBuilder out, Map<?, ?> table, String path) {
        if (!path.isEmpty()) out.append('\n').append('[').append(path).append("]\n");
        for (var entry : table.entrySet()) if (!(entry.getValue() instanceof Map<?, ?>))
            out.append(quote(entry.getKey().toString())).append(" = ").append(literal(entry.getValue())).append('\n');
        for (var entry : table.entrySet()) if (entry.getValue() instanceof Map<?, ?> nested)
            writeTable(out, nested, (path.isEmpty() ? "" : path + ".") + quote(entry.getKey().toString()));
    }
    private static String literal(Object value) {
        if (value instanceof String text) return quote(text);
        if (value instanceof Long || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) return "[" + String.join(", ", list.stream().map(ConfigMigration::literal).toList()) + "]";
        throw new IllegalArgumentException("Nicht unterstützter Konfigurationswert: " + value);
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) switch (c) {
            case '\\' -> out.append("\\\\"); case '"' -> out.append("\\\""); case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
}
