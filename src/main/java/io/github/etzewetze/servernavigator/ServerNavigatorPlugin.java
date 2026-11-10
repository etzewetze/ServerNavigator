package io.github.etzewetze.servernavigator;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.core.NavigationEngine;
import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.provider.ConnectionWakeupProvider;
import io.github.etzewetze.servernavigator.provider.HttpWakeupProvider;
import io.github.etzewetze.servernavigator.provider.VelocityLoginReadinessChecker;
import io.github.etzewetze.servernavigator.velocity.CommandRegistry;
import io.github.etzewetze.servernavigator.velocity.MessageRenderer;
import io.github.etzewetze.servernavigator.velocity.VelocityClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public final class ServerNavigatorPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private final MessageRenderer renderer = new MessageRenderer();
    private final Map<UUID, VelocityClient> clients = new ConcurrentHashMap<>();
    private volatile NavigationEngine engine;
    private volatile boolean stopping;
    private CommandRegistry registry;
    private volatile HttpWakeupProvider http;
    private final ConnectionWakeupProvider connectionWakeup = new ConnectionWakeupProvider();
    private final AdminCommand admin = new AdminCommand();

    @Inject public ServerNavigatorPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path directory) {
        this.proxy = proxy; this.logger = logger; this.directory = directory;
    }
    @Subscribe public void initialize(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve("config.toml");
            if (!Files.exists(path)) try (var resource = getClass().getResourceAsStream("/config.toml")) {
                if (resource == null) throw new IOException("Eingebettete config.toml fehlt");
                Files.copy(resource, path);
            }
            ConfigLoader.Candidate candidate = load();
            NavigatorConfig initial = candidate.config();
            registry = new CommandRegistry(this, proxy.getCommandManager()); registry.validate(initial);
            http = new HttpWakeupProvider(initial.attemptTimeout());
            engine = new NavigationEngine(initial, (action, interval) -> {
                var task = proxy.getScheduler().buildTask(this, action).repeat(interval).schedule(); return task::cancel;
            }, () -> System.nanoTime() / 1_000_000,
                backend -> backend.wake().type() == NavigatorConfig.WakeType.CONNECTION ? connectionWakeup : http,
                new VelocityLoginReadinessChecker());
            registry.install(initial, admin, this::navigationCommand);
            saveMigration(candidate);
            logger.info("ServerNavigator gestartet: {} Ziele; unabhängige Verbindungsprüfung.", initial.servers().size());
        } catch (Exception error) {
            logger.error("ServerNavigator konnte nicht aktiviert werden. Konfiguration/Befehlskonflikte prüfen.", error);
            if (engine != null) engine.close(); engine = null;
            if (registry != null) registry.close(); if (http != null) http.close();
        }
    }
    private ConfigLoader.Candidate load() throws IOException {
        return new ConfigLoader().loadCandidate(directory.resolve("config.toml"), name -> proxy.getServer(name).isPresent());
    }
    private void saveMigration(ConfigLoader.Candidate candidate) {
        if (!candidate.migrated()) return;
        try {
            Path backup = candidate.saveMigration(directory.resolve("config.toml"));
            logger.info("Konfiguration auf Format 2 aktualisiert. Kompaktes Anzeige-Layout aktiviert; Sicherung: {}", backup.getFileName());
        } catch (IOException failure) {
            logger.warn("Die neue Konfiguration ist aktiv, konnte aber nicht gespeichert werden: {}", failure.getMessage());
        }
    }
    private VelocityClient client(Player p) {
        return clients.computeIfAbsent(p.getUniqueId(), ignored -> new VelocityClient(p, proxy, () -> engine.config(), renderer));
    }
    private SimpleCommand navigationCommand(String id) {
        return new SimpleCommand() {
            @Override public void execute(Invocation call) {
                NavigationEngine current = engine; if (current == null) return;
                if (!(call.source() instanceof Player p)) { send(call.source(), "player-only", Progress.basic(null)); return; }
                if (call.arguments().length != 0) { send(call.source(), "usage", Progress.basic(null)); return; }
                current.navigate(client(p), id);
            }
            // Returning false here forwards SimpleCommands to the backend. Keep the proxy handler
            // authoritative and produce our explicit permission denial instead.
            @Override public boolean hasPermission(Invocation call) { return true; }
        };
    }
    private void send(CommandSource source, String key, Progress progress) {
        NavigationEngine current = engine;
        if (current != null) source.sendMessage(renderer.render(current.config().messages().get(key), progress));
    }
    private synchronized void reload(CommandSource source) {
        if (stopping || engine == null) return;
        try {
            ConfigLoader.Candidate candidate = load();
            NavigatorConfig replacement = candidate.config(); registry.validate(replacement);
            // Construct all resources first. The active snapshot is replaced only after successful
            // validation and command registration. Old requests are explicitly cancelled on commit.
            var nextHttp = new HttpWakeupProvider(replacement.attemptTimeout());
            try { registry.install(replacement, admin, this::navigationCommand); }
            catch (RuntimeException failure) { nextHttp.close(); throw failure; }
            var previousHttp = http; http = nextHttp; engine.reload(replacement); previousHttp.close();
            saveMigration(candidate);
            send(source, "reloaded", Progress.basic(null));
        } catch (Exception error) {
            logger.warn("Konfigurations-Reload abgelehnt: {}", error.getMessage());
            send(source, "reload-failed", Progress.basic(null).reason(Component.text(error.getMessage() == null ? "Ungültige Konfiguration" : error.getMessage())));
        }
    }
    private final class AdminCommand implements SimpleCommand {
        @Override public void execute(Invocation call) {
            if (engine == null) return;
            String[] args = call.arguments();
            if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
                if (call.source() instanceof Player p) engine.cancel(client(p));
                else send(call.source(), "player-only", Progress.basic(null));
                return;
            }
            if (!call.source().hasPermission("servernavigator.admin")) { send(call.source(), "no-permission", Progress.basic(null)); return; }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                proxy.getScheduler().buildTask(ServerNavigatorPlugin.this, () -> reload(call.source())).schedule(); return;
            }
            if ((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("status")) {
                var statuses = engine.status(args.length == 2 ? args[1] : null);
                if (statuses.isEmpty()) send(call.source(), "unknown-server", Progress.basic(null));
                for (Progress p : statuses) send(call.source(), p.count() == 0 ? "status-idle" : "status-active", p);
                return;
            }
            send(call.source(), "usage", Progress.basic(null));
        }
        @Override public boolean hasPermission(Invocation call) { return true; }
        @Override public List<String> suggest(Invocation call) {
            String[] args = call.arguments();
            if (args.length <= 1) return (call.source().hasPermission("servernavigator.admin") ? List.of("cancel", "status", "reload") : List.of("cancel"))
                .stream().filter(s -> args.length == 0 || s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 2 && args[0].equalsIgnoreCase("status") && call.source().hasPermission("servernavigator.admin"))
                return engine.config().servers().keySet().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
            return List.of();
        }
    }
    @Subscribe(order = PostOrder.LAST) public void beforeConnect(ServerPreConnectEvent event) {
        NavigationEngine current = engine;
        if (current == null) return;
        VelocityClient c = client(event.getPlayer());
        String original = event.getOriginalServer().getServerInfo().getName();
        String selected = event.getResult().getServer().map(s -> s.getServerInfo().getName()).orElse(original);
        if (!current.mayAccess(c, original) || !current.mayAccess(c, selected)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            current.accessDenied(c, !current.mayAccess(c, original) ? original : selected); return;
        }
        VelocityClient.Permit permit = c.permit();
        if (permit != null && permit.target().equalsIgnoreCase(original) && !permit.valid().getAsBoolean())
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }
    @Subscribe public void connected(ServerConnectedEvent event) {
        NavigationEngine current = engine;
        if (current != null) current.serverConnected(event.getPlayer().getUniqueId(), event.getServer().getServerInfo().getName());
    }
    @Subscribe public void disconnected(DisconnectEvent event) {
        NavigationEngine current = engine;
        UUID id = event.getPlayer().getUniqueId();
        if (current != null) current.disconnect(id);
        VelocityClient c = clients.remove(id); if (c != null) c.clearDisplay();
    }
    @Subscribe public synchronized void shutdown(ProxyShutdownEvent event) {
        stopping = true;
        if (engine != null) engine.close();
        if (registry != null) registry.close();
        if (http != null) http.close();
        clients.values().forEach(VelocityClient::clearDisplay); clients.clear();
        proxy.getScheduler().tasksByPlugin(this).forEach(task -> task.cancel());
    }
}
