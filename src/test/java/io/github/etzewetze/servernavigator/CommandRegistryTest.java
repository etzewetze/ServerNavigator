package io.github.etzewetze.servernavigator;

import static org.junit.jupiter.api.Assertions.*;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.velocity.CommandRegistry;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRegistryTest {
    @TempDir Path temp;
    NavigatorConfig config;
    String source;
    Object owner = new Object();
    Map<String, CommandMeta> metadata = new HashMap<>();
    Map<String, Command> commands = new HashMap<>();
    String failOnce;
    CommandManager manager;
    CommandRegistry registry;
    SimpleCommand noop = call -> {};

    @BeforeEach void setup() throws Exception {
        try (var in = getClass().getResourceAsStream("/config.toml")) { source = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        config = load(source);
        manager = (CommandManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandManager.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "hasCommand" -> metadata.containsKey((String) args[0]);
                case "getCommandMeta" -> metadata.get((String) args[0]);
                case "getAliases" -> List.copyOf(metadata.keySet());
                case "metaBuilder" -> builder((String) args[0]);
                case "register" -> {
                    CommandMeta meta = (CommandMeta) args[0];
                    if (failOnce != null && meta.getAliases().contains(failOnce)) { failOnce = null; throw new IllegalArgumentException("simulated race"); }
                    for (String alias : meta.getAliases()) if (metadata.containsKey(alias)) throw new IllegalArgumentException("collision");
                    for (String alias : meta.getAliases()) { metadata.put(alias, meta); commands.put(alias, (Command) args[1]); }
                    yield null;
                }
                case "unregister" -> { metadata.remove((String) args[0]); commands.remove((String) args[0]); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        registry = new CommandRegistry(owner, manager);
    }
    NavigatorConfig load(String content) throws Exception { Path file = temp.resolve("config.toml"); Files.writeString(file, content); return new ConfigLoader().load(file, s -> true); }
    CommandMeta.Builder builder(String main) {
        List<String> names = new ArrayList<>(List.of(main)); Object[] plugin = new Object[1];
        return (CommandMeta.Builder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandMeta.Builder.class}, (proxy, method, args) -> switch (method.getName()) {
            case "aliases" -> { names.addAll(List.of((String[]) args[0])); yield proxy; }
            case "plugin" -> { plugin[0] = args[0]; yield proxy; }
            case "build" -> meta(names, plugin[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    CommandMeta meta(List<String> names, Object plugin) {
        return (CommandMeta) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandMeta.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getAliases" -> List.copyOf(names); case "getPlugin" -> plugin; case "getHints" -> List.of();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    @Test void foreignAliasConflictDoesNotOverwriteAnyCommand() {
        var foreign = meta(List.of("hub"), new Object()); metadata.put("hub", foreign);
        assertThrows(IllegalArgumentException.class, () -> registry.install(config, noop, ignored -> noop));
        assertSame(foreign, metadata.get("hub")); assertEquals(1, metadata.size());
    }
    @Test void aliasesUseExactlyTheSameHandlerAndReloadRemovesOldAliases() throws Exception {
        registry.install(config, noop, ignored -> (SimpleCommand) call -> {});
        assertSame(commands.get("lobby"), commands.get("hub")); assertSame(commands.get("lobby"), commands.get("l"));
        var next = load(source.replace("aliases = [\"surv\"]", "aliases = [\"sv\"]"));
        registry.install(next, noop, ignored -> noop);
        assertFalse(metadata.containsKey("surv")); assertTrue(metadata.containsKey("sv"));
    }
    @Test void failedRegistrationRestoresPriorCommandsAndKeepsForeignCommands() throws Exception {
        registry.install(config, noop, ignored -> noop); var names = java.util.Set.copyOf(metadata.keySet());
        var foreign = meta(List.of("foreign"), new Object()); metadata.put("foreign", foreign);
        var next = load(source.replace("aliases = [\"surv\"]", "aliases = [\"sv\"]")); failOnce = "sv";
        assertThrows(IllegalArgumentException.class, () -> registry.install(next, noop, ignored -> noop));
        assertTrue(metadata.keySet().containsAll(names)); assertFalse(metadata.containsKey("sv"));
        assertSame(foreign, metadata.get("foreign"));
        registry.close(); assertEquals(java.util.Set.of("foreign"), metadata.keySet());
    }
}
