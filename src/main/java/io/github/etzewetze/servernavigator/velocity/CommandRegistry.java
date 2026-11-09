package io.github.etzewetze.servernavigator.velocity;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** No foreign alias is unregistered. Failed registration restores the previous command set. */
public final class CommandRegistry implements AutoCloseable {
    private record Entry(CommandMeta meta, Command command) { }
    private final Object plugin;
    private final CommandManager manager;
    private final List<Entry> entries = new ArrayList<>();
    public CommandRegistry(Object plugin, CommandManager manager) { this.plugin = plugin; this.manager = manager; }
    public void validate(NavigatorConfig config) {
        check("servernavigator");
        for (Backend server : config.servers().values()) for (String alias : server.commandNames()) check(alias);
    }
    private void check(String alias) {
        if (!manager.hasCommand(alias)) return;
        var meta = manager.getCommandMeta(alias);
        if (meta == null || meta.getPlugin() != plugin) throw new IllegalArgumentException("Proxy-Befehl bereits fremd belegt: /" + alias);
    }
    public synchronized void install(NavigatorConfig config, Command admin, Function<String, Command> factory) {
        validate(config);
        List<Entry> candidate = new ArrayList<>();
        candidate.add(new Entry(manager.metaBuilder("servernavigator").plugin(plugin).build(), admin));
        for (Backend server : config.servers().values()) candidate.add(new Entry(manager.metaBuilder(server.command())
            .aliases(server.aliases().toArray(String[]::new)).plugin(plugin).build(), factory.apply(server.id())));
        List<Entry> previous = List.copyOf(entries); List<Entry> installed = new ArrayList<>();
        removeOwned(previous);
        try {
            for (Entry entry : candidate) {
                for (String alias : entry.meta().getAliases()) check(alias);
                manager.register(entry.meta(), entry.command()); installed.add(entry);
            }
            entries.clear(); entries.addAll(candidate);
        } catch (RuntimeException failure) {
            removeOwned(installed);
            for (Entry old : previous) {
                for (String alias : old.meta().getAliases()) check(alias);
                manager.register(old.meta(), old.command());
            }
            throw failure;
        }
    }
    private void removeOwned(List<Entry> selected) {
        for (Entry entry : selected) for (String alias : entry.meta().getAliases()) {
            var current = manager.getCommandMeta(alias);
            if (current != null && current.getPlugin() == plugin) manager.unregister(alias);
        }
    }
    @Override public synchronized void close() { removeOwned(entries); entries.clear(); }
}
